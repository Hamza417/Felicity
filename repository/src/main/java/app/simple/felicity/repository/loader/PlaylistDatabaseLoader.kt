package app.simple.felicity.repository.loader

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import app.simple.felicity.core.m3u.M3uParser
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import app.simple.felicity.repository.scanners.AudioScanner
import app.simple.felicity.repository.scanners.SAFFile
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

        // Read the live list of granted tree URIs directly from the system instead
        // of a separate preferences store — this way revoked permissions are noticed
        // immediately without any stale state to worry about.
        val treeUriStrings = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }

        val playlistDao = audioDatabase.playlistDao()
        val audioDao = audioDatabase.audioDao() ?: run {
            Log.e(TAG, "AudioDao is unavailable — skipping M3U processing.")
            return
        }

        // Gather every M3U file from all SAF tree URIs the user has granted.
        val m3uSAFFiles = mutableListOf<SAFFile>()
        val scanner = AudioScanner()
        for (uriStr in treeUriStrings) {
            val treeUri = uriStr.toUri()
            val found = scanner.getM3uFiles(context, treeUri)
            Log.d(TAG, "Found ${found.size} M3U file(s) in $uriStr")
            m3uSAFFiles.addAll(found)
        }

        // Map existing M3U playlists by their stored source URI for O(1) lookup.
        val existingM3uPlaylists = playlistDao.getAllM3uPlaylists()
        val existingByPath = existingM3uPlaylists.associateBy { it.m3uFilePath ?: "" }

        val visitedPaths = mutableSetOf<String>()

        for (m3uFile in m3uSAFFiles) {
            val uriKey = m3uFile.uri.toString()
            visitedPaths.add(uriKey)

            val existing = existingByPath[uriKey]
            val fileLastModified = m3uFile.lastModified

            if (existing != null && existing.dateModified >= fileLastModified) {
                Log.d(TAG, "M3U playlist unchanged, skipping: ${m3uFile.name}")
                continue
            }

            // Read the M3U content via the ContentResolver — this is the SAF way of
            // opening a file we don't have a direct file-system path to.
            val content = runCatching {
                context.contentResolver.openInputStream(m3uFile.uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.readText()
            }.getOrNull() ?: run {
                Log.e(TAG, "Failed to read M3U file: $uriKey")
                continue
            }

            val m3uPlaylist = M3uParser.parse(content)

            val playlistName = m3uPlaylist.name?.takeIf { it.isNotBlank() }
                ?: m3uFile.name.substringBeforeLast('.').ifBlank { "Unnamed Playlist" }

            val playlistId: Long

            if (existing == null) {
                playlistId = playlistDao.insertPlaylist(
                        Playlist(
                                name = playlistName,
                                isM3UPlaylist = true,
                                m3uFilePath = uriKey,
                                dateCreated = System.currentTimeMillis(),
                                dateModified = fileLastModified
                        )
                )
                Log.d(TAG, "Created M3U playlist '$playlistName' (id=$playlistId) from: ${m3uFile.name}")
            } else {
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

            val crossRefs = mutableListOf<PlaylistSongCrossRef>()

            for ((index, entry) in m3uPlaylist.entries.withIndex()) {
                val existingAudio = audioDao.getAudioByPath(entry.path)
                    ?: audioDao.getAudioByPath(entry.path.substringAfterLast('/'))

                val audioHash = if (existingAudio != null) {
                    existingAudio.hash
                } else {
                    val ghost = buildGhostAudio(entry.path, entry.title)
                    val insertedRowId = audioDao.insertOrIgnore(ghost)
                    if (insertedRowId == -1L) {
                        Log.w(TAG, "Ghost insert skipped (hash collision) for: ${entry.path}")
                    } else {
                        Log.d(TAG, "Inserted ghost for unscanned track: ${entry.path}")
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

        // Remove playlists whose M3U source is no longer in our scanned set.
        val stalePlaylists = existingM3uPlaylists.filter { playlist ->
            playlist.m3uFilePath != null && playlist.m3uFilePath !in visitedPaths
        }

        if (stalePlaylists.isNotEmpty()) {
            stalePlaylists.forEach { playlist ->
                Log.d(TAG, "Removing stale M3U playlist '${playlist.name}' — source gone: ${playlist.m3uFilePath}")
                playlistDao.deletePlaylist(playlist)
            }
        }

        Log.d(TAG, "M3U playlist processing done in ${(System.currentTimeMillis() - startTime) / 1000}s. " +
                "Processed ${m3uSAFFiles.size} file(s), removed ${stalePlaylists.size} stale playlist(s).")
    }

    /**
     * Builds a minimal placeholder [Audio] entry for a track that appears in an M3U
     * file but hasn't been scanned into the library yet.
     *
     * The placeholder is marked unavailable so it stays hidden from normal library views.
     * It will be replaced with real data on the next audio scan, or quietly deleted when
     * the reconcile pass notices the file doesn't actually exist.
     *
     * @param rawPath  The raw path string from the M3U entry.
     * @param displayTitle The title from the M3U's EXTINF tag, used as a fallback name.
     * @return A ready-to-insert placeholder [Audio] object.
     */
    private fun buildGhostAudio(rawPath: String, displayTitle: String?): Audio {
        val file = File(rawPath)
        val audio = Audio()
        audio.name = file.name
        audio.setTitle(displayTitle ?: file.nameWithoutExtension)
        audio.uri = rawPath
        audio.mimeType = file.extension.lowercase()
        audio.hash = hashPath(rawPath)
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
