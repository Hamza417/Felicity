package app.simple.felicity.repository.loader

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import app.simple.felicity.core.m3u.M3uParser
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import app.simple.felicity.repository.scanners.AudioScanner
import app.simple.felicity.shared.storage.RemovableStorageDetector
import app.simple.felicity.shared.utils.ProcessUtils.checkNotMainThread
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the device's storage for M3U playlist files and keeps the database in sync
 * with whatever it finds.
 *
 * <p>This loader runs after the audio scan finishes so that the tracks referenced
 * inside each M3U file are already in the database by the time we try to link
 * them. Any track that is still missing gets a lightweight "ghost" placeholder
 * entry so the playlist slot is never left empty.</p>
 *
 * <p>On every run the loader does three things:</p>
 * <ol>
 *   <li>Creates a new playlist for every M3U file that has no corresponding
 *       database row yet.</li>
 *   <li>Refreshes the songs of any M3U playlist whose source file has changed
 *       since the last scan (detected via last-modified timestamp).</li>
 *   <li>Deletes playlists whose M3U source file has disappeared from disk.</li>
 * </ol>
 *
 * @author Hamza417
 */
@Singleton
@WorkerThread
class PlaylistDatabaseLoader @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlaylistDatabaseLoader"
    }

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /**
     * Entry point called by the service after the audio scan completes.
     * Finds all M3U files on mounted storage, then creates, updates, or removes
     * playlists as needed to match the current state of those files.
     */
    suspend fun processM3uFiles() {
        checkNotMainThread()

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting M3U playlist processing...")

        val storages = RemovableStorageDetector.getAllStorageVolumes(context)
        val playlistDao = audioDatabase.playlistDao()
        val audioDao = audioDatabase.audioDao() ?: run {
            Log.e(TAG, "AudioDao is unavailable — skipping M3U processing.")
            return
        }

        // Gather every M3U file from all mounted storage volumes.
        val m3uFiles = mutableListOf<File>()
        storages.forEach { storage ->
            val rootPath = storage?.path ?: return@forEach
            val found = AudioScanner().getM3uFiles(rootPath)
            Log.d(TAG, "Found ${found.size} M3U file(s) in $rootPath")
            m3uFiles.addAll(found)
        }

        // Load all playlists that the scanner previously created from M3U files.
        // We map them by source path so lookup is O(1) per file.
        val existingM3uPlaylists = playlistDao.getAllM3uPlaylists()
        val existingByPath = existingM3uPlaylists.associateBy { it.m3uFilePath ?: "" }

        // Keep track of every M3U path we process so we know which stored
        // playlists are now orphaned at the end.
        val visitedPaths = mutableSetOf<String>()

        for (m3uFile in m3uFiles) {
            val filePath = m3uFile.absolutePath
            visitedPaths.add(filePath)

            val existing = existingByPath[filePath]
            val fileLastModified = m3uFile.lastModified()

            // If a playlist already exists and the file hasn't changed since we last
            // read it, there's nothing to do — skip and save some battery.
            if (existing != null && existing.dateModified >= fileLastModified) {
                Log.d(TAG, "M3U playlist unchanged, skipping: ${m3uFile.name}")
                continue
            }

            // Parse the M3U file into a structured object.
            val content = runCatching { m3uFile.readText(Charsets.UTF_8) }.getOrNull() ?: run {
                Log.e(TAG, "Failed to read M3U file: $filePath")
                continue
            }
            val m3uPlaylist = M3uParser.parse(content)

            // Use the #PLAYLIST tag name if present, otherwise fall back to the
            // file's own name without the extension — perfectly reasonable.
            val playlistName = m3uPlaylist.name?.takeIf { it.isNotBlank() }
                ?: m3uFile.nameWithoutExtension

            val playlistId: Long

            if (existing == null) {
                // Brand-new M3U file — create a fresh playlist for it.
                playlistId = playlistDao.insertPlaylist(
                        Playlist(
                                name = playlistName,
                                isM3UPlaylist = true,
                                m3uFilePath = filePath,
                                dateCreated = System.currentTimeMillis(),
                                dateModified = fileLastModified
                        )
                )
                Log.d(TAG, "Created M3U playlist '$playlistName' (id=$playlistId) from: ${m3uFile.name}")
            } else {
                // The file exists but its content changed — clear the old songs and
                // repopulate from the updated file.
                playlistId = existing.id
                playlistDao.removeAllSongsFromPlaylist(playlistId)
                playlistDao.updatePlaylist(
                        existing.copy(
                                name = playlistName,
                                dateModified = fileLastModified
                        )
                )
                Log.d(TAG, "Refreshing M3U playlist '$playlistName' (id=$playlistId) from: ${m3uFile.name}")
            }

            // Resolve each track entry and link it to the playlist.
            val baseDir = m3uFile.parent
            val crossRefs = mutableListOf<PlaylistSongCrossRef>()

            for ((index, entry) in m3uPlaylist.entries.withIndex()) {
                val absolutePath = resolveAbsolutePath(entry.path, baseDir)
                val existingAudio = audioDao.getAudioByPath(absolutePath)

                val audioHash = if (existingAudio != null) {
                    // Track is already in the library — link it directly.
                    existingAudio.hash
                } else {
                    // Track is not in the library yet. Insert a ghost placeholder so
                    // the playlist still reserves the slot. The audio scanner will
                    // replace the ghost with real metadata on its next full pass.
                    val ghost = buildGhostAudio(absolutePath, entry.title)
                    val insertedRowId = audioDao.insertOrIgnore(ghost)
                    if (insertedRowId == -1L) {
                        Log.w(TAG, "Ghost insert skipped (hash collision) for: $absolutePath")
                    } else {
                        Log.d(TAG, "Inserted ghost for unscanned track: $absolutePath")
                    }
                    ghost.hash
                }

                crossRefs.add(
                        PlaylistSongCrossRef(
                                playlistId = playlistId,
                                audioHash = audioHash,
                                position = index
                        )
                )
            }

            playlistDao.addSongsToPlaylist(crossRefs)
            Log.d(TAG, "Linked ${crossRefs.size} track(s) to '$playlistName'")
        }

        // Any M3U playlist whose source file we didn't visit is either on an
        // unmounted volume or has been deleted. Delete those stale playlists.
        val stalePlaylists = existingM3uPlaylists.filter { playlist ->
            playlist.m3uFilePath != null && playlist.m3uFilePath !in visitedPaths
        }

        if (stalePlaylists.isNotEmpty()) {
            stalePlaylists.forEach { playlist ->
                Log.d(TAG, "Removing stale M3U playlist '${playlist.name}' — source file gone: ${playlist.m3uFilePath}")
                playlistDao.deletePlaylist(playlist)
            }
        }

        Log.d(TAG, "M3U playlist processing done in ${(System.currentTimeMillis() - startTime) / 1000}s. " +
                "Processed ${m3uFiles.size} file(s), removed ${stalePlaylists.size} stale playlist(s).")
    }

    /**
     * Turns a path string from the M3U file into an absolute file-system path.
     *
     * <p>If the path is already absolute (starts with '/') or looks like a network URL
     * (contains '://') it is returned as-is. Relative paths get prepended with the
     * directory that contains the M3U file. Windows-style backslashes are normalized
     * to forward slashes because some tools just can't help themselves.</p>
     *
     * @param rawPath The path exactly as it appeared in the M3U file.
     * @param baseDir The directory containing the M3U file, or null if unknown.
     * @return The best absolute path we can produce for this entry.
     */
    private fun resolveAbsolutePath(rawPath: String, baseDir: String?): String {
        val normalized = rawPath.replace('\\', '/')
        return when {
            normalized.startsWith('/') -> normalized
            normalized.contains("://") -> normalized
            baseDir != null -> "$baseDir/$normalized"
            else -> normalized
        }
    }

    /**
     * Builds a minimal placeholder [Audio] entry for a track that appears in an M3U
     * file but hasn't been scanned into the library yet.
     *
     * <p>The placeholder is marked as unavailable so it stays invisible in all normal
     * library views. It will either be replaced with real data on the next audio scan,
     * or quietly deleted when the reconcile pass notices the file doesn't actually exist.</p>
     *
     * @param absolutePath The resolved file path of the missing track.
     * @param displayTitle The title from the M3U's EXTINF tag, used as a fallback name.
     * @return A ready-to-insert placeholder [Audio] object.
     */
    private fun buildGhostAudio(absolutePath: String, displayTitle: String?): Audio {
        val file = File(absolutePath)
        val audio = Audio()
        audio.name = file.name
        audio.setTitle(displayTitle ?: file.nameWithoutExtension)
        audio.path = absolutePath
        audio.mimeType = file.extension.lowercase()
        audio.hash = hashPath(absolutePath)
        audio.size = 0L
        audio.duration = 0L
        audio.bitrate = 0L
        audio.samplingRate = 0L
        audio.bitPerSample = 0L
        audio.dateAdded = System.currentTimeMillis()
        audio.dateModified = 0L
        audio.dateTaken = 0L
        audio.numTracks = "0"
        // Mark as unavailable so it won't appear in normal library queries.
        audio.isAvailable = false
        return audio
    }

    /**
     * Produces a deterministic 64-bit hash from a file path string.
     *
     * <p>We intentionally prefix the path with a unique identifier ("ghost:")
     * before hashing. This guarantees that the hash space for file paths is
     * completely isolated from the real metadata hashes, preventing any accidental
     * collisions between a ghost entry and a real library entry.</p>
     *
     * @param path The absolute file path to hash.
     * @return A 64-bit hash value for use as [Audio.hash].
     */
    private fun hashPath(path: String): Long {
        // The prefix acts as our "seed" to separate the hash spaces
        val saltedPath = "ghost:$path"

        // Generate the fast, native 128-bit MD5 hash
        val digest = MessageDigest.getInstance("MD5").digest(saltedPath.toByteArray(Charsets.UTF_8))

        // Extract the first 8 bytes directly into a 64-bit Long
        return ByteBuffer.wrap(digest).long
    }
}

