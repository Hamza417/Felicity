package app.simple.felicity.repository.loader

import android.content.Context
import android.provider.DocumentsContract
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
 * Scans every SAF tree the user has granted for M3U playlist files, then keeps
 * the local Room database in sync with whatever it finds.
 *
 * <p>The matching strategy mirrors how [AudioRepository.getFolderContents] works —
 * everything is done through SAF document IDs extracted from the content URIs already
 * stored in the database. No extra MediaStore queries, no POSIX path gymnastics.</p>
 *
 * <p>On every run the loader does three things:</p>
 * <ol>
 *   <li>Creates a new playlist row for every M3U file that has no row yet.</li>
 *   <li>Refreshes the song links of any M3U playlist whose source file has changed
 *       since the last scan (detected via last-modified timestamp).</li>
 *   <li>Deletes playlist rows whose M3U source file has disappeared from storage.</li>
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
     * Entry point — called by [AudioDatabaseService] right after the audio scan finishes
     * so every track referenced by M3U files is guaranteed to be in the database already.
     */
    suspend fun processM3uFiles() {
        checkNotMainThread()

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Starting M3U playlist processing...")

        val treeUriStrings = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }

        val playlistDao = audioDatabase.playlistDao()
        val audioDao = audioDatabase.audioDao() ?: run {
            Log.e(TAG, "AudioDao is unavailable — skipping M3U processing.")
            return
        }

        // Pull every audio row once and build two lookup tables from their SAF document IDs.
        // This mirrors the exact same approach AudioRepository.getFolderContents() uses for
        // building folder trees, so the two systems are always in sync.
        //
        //   audioByDirAndName  →  parentDocId → (lowercase filename → Audio)
        //     Same-directory match: M3U parent doc ID + entry filename.
        //     E.g. M3U at "primary:Music/Playlists/mix.m3u" looks in "primary:Music/Playlists".
        //
        //   audioByFilename  →  lowercase filename → Audio  (first match wins)
        //     Fallback when the directory doesn't match (absolute paths from another device, etc.)
        val allAudio = audioDao.getAllAudioListAll()
        val audioByDirAndName = HashMap<String, HashMap<String, Audio>>(allAudio.size)
        val audioByFilename = HashMap<String, Audio>(allAudio.size * 2)

        for (audio in allAudio) {
            val docId = docIdOf(audio.uri) ?: continue
            val parentDocId = docId.substringBeforeLast('/', missingDelimiterValue = "")
            val filename = docId.substringAfterLast('/').lowercase()
            if (filename.isEmpty()) continue

            audioByDirAndName
                .getOrPut(parentDocId) { HashMap() }
                .putIfAbsent(filename, audio)

            audioByFilename.putIfAbsent(filename, audio)
        }

        Log.d(TAG, "Built lookup index: ${allAudio.size} rows, " +
                "${audioByDirAndName.size} unique parent directories.")

        // Collect every M3U file from all granted SAF trees.
        val m3uFiles = mutableListOf<SAFFile>()
        val scanner = AudioScanner()
        for (uriStr in treeUriStrings) {
            val found = scanner.getM3uFiles(context, uriStr.toUri())
            Log.d(TAG, "Found ${found.size} M3U file(s) in $uriStr")
            m3uFiles.addAll(found)
        }

        val existingPlaylists = playlistDao.getAllM3uPlaylists()
        val existingByKey = existingPlaylists.associateBy { it.m3uFilePath ?: "" }
        val visitedKeys = mutableSetOf<String>()

        for (m3uFile in m3uFiles) {
            val uriKey = m3uFile.uri.toString()
            visitedKeys.add(uriKey)

            val existing = existingByKey[uriKey]
            if (existing != null && existing.dateModified >= m3uFile.lastModified) {
                Log.d(TAG, "Unchanged, skipping: ${m3uFile.name}")
                continue
            }

            // Open the M3U text through the ContentResolver — SAF-safe, no file descriptor
            // tricks needed, just a plain input stream from the content URI.
            val content = runCatching {
                context.contentResolver.openInputStream(m3uFile.uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.readText()
            }.getOrNull() ?: run {
                Log.e(TAG, "Could not read M3U file: $uriKey")
                continue
            }

            val parsed = M3uParser.parse(content)
            val playlistName = parsed.name?.takeIf { it.isNotBlank() }
                ?: m3uFile.name.substringBeforeLast('.').ifBlank { "Unnamed Playlist" }

            // Extract the SAF document ID of the M3U file and strip its filename to get the
            // parent directory document ID. Track entries that are bare filenames or relative
            // paths will be resolved against this parent directory first.
            // E.g. "primary:Music/Playlists/mix.m3u" → parent is "primary:Music/Playlists"
            val m3uDocId = docIdOf(uriKey)
            val m3uParentDocId = m3uDocId?.substringBeforeLast('/', missingDelimiterValue = "")
            Log.d(TAG, "Processing '${m3uFile.name}' (parent doc: '$m3uParentDocId')")

            val playlistId: Long = if (existing == null) {
                playlistDao.insertPlaylist(
                        Playlist(
                                name = playlistName,
                                isM3UPlaylist = true,
                                m3uFilePath = uriKey,
                                dateCreated = System.currentTimeMillis(),
                                dateModified = m3uFile.lastModified
                        )
                ).also { Log.d(TAG, "Created playlist '$playlistName' id=$it") }
            } else {
                playlistDao.removeAllSongsFromPlaylist(existing.id)
                playlistDao.updatePlaylist(existing.copy(
                        name = playlistName,
                        dateModified = m3uFile.lastModified
                ))
                Log.d(TAG, "Refreshing playlist '$playlistName' id=${existing.id}")
                existing.id
            }

            val crossRefs = mutableListOf<PlaylistSongCrossRef>()

            for ((index, entry) in parsed.entries.withIndex()) {
                // Normalize Windows-style backslashes — common when M3U files are made on a PC.
                val entryPath = entry.path.replace('\\', '/')
                val entryFilename = entryPath.substringAfterLast('/').lowercase()

                val resolved = resolveEntry(
                        entryFilename = entryFilename,
                        entryPath = entryPath,
                        m3uParentDocId = m3uParentDocId,
                        audioByDirAndName = audioByDirAndName,
                        audioByFilename = audioByFilename
                )

                val hash = if (resolved != null) {
                    Log.d(TAG, "  ✓ '${entry.path}' → '${docIdOf(resolved.uri)}'")
                    resolved.hash
                } else {
                    Log.w(TAG, "  ✗ No match for '${entry.path}' — creating ghost.")
                    val ghost = buildGhostAudio(entryPath, entry.title)
                    audioDao.insertOrIgnore(ghost)
                    ghost.hash
                }

                crossRefs.add(PlaylistSongCrossRef(playlistId, hash, index))
            }

            playlistDao.addSongsToPlaylist(crossRefs)
            Log.d(TAG, "Linked ${crossRefs.size} track(s) to '$playlistName'")
        }

        // Remove playlist rows whose M3U source file no longer exists anywhere on storage.
        val stale = existingPlaylists.filter {
            it.m3uFilePath != null && it.m3uFilePath !in visitedKeys
        }
        stale.forEach {
            Log.d(TAG, "Removing stale playlist '${it.name}' (gone: ${it.m3uFilePath})")
            playlistDao.deletePlaylist(it)
        }

        Log.d(TAG, "M3U processing done in ${(System.currentTimeMillis() - startTime) / 1000}s — " +
                "${m3uFiles.size} file(s) processed, ${stale.size} stale playlist(s) removed.")
    }

    /**
     * Tries to match an M3U entry to an [Audio] row in the database using SAF document IDs.
     *
     * The lookup runs through three strategies in order of reliability:
     *
     * 1. **Same-directory match** — the filename is looked up in the exact SAF parent folder
     *    that contains the M3U file. This covers the vast majority of real playlists where
     *    every track sits right next to the playlist file.
     *
     * 2. **Relative sub-path match** — if the entry looks like "SubFolder/song.mp3", the
     *    sub-folder is appended to the M3U's parent doc ID and we check that folder.
     *
     * 3. **Filename-anywhere match** — the filename is looked up across the entire library
     *    regardless of which folder the file lives in. This is the widest net and catches
     *    absolute paths from other devices or tracks that moved folders.
     *
     * @param entryFilename   Lowercase filename extracted from the M3U entry path.
     * @param entryPath       Full normalized path from the M3U entry (for sub-path detection).
     * @param m3uParentDocId  SAF document ID of the folder containing the M3U file.
     * @param audioByDirAndName parentDocId → (filename → Audio) lookup.
     * @param audioByFilename   filename → Audio lookup (any folder).
     * @return The matched [Audio], or null if nothing was found.
     */
    private fun resolveEntry(
            entryFilename: String,
            entryPath: String,
            m3uParentDocId: String?,
            audioByDirAndName: Map<String, Map<String, Audio>>,
            audioByFilename: Map<String, Audio>
    ): Audio? {
        if (entryFilename.isEmpty()) return null

        // Strategy 1 — same directory as the M3U file itself.
        if (m3uParentDocId != null) {
            audioByDirAndName[m3uParentDocId]?.get(entryFilename)?.let { return it }
        }

        // Strategy 2 — relative path with a sub-folder segment, e.g. "Rock/song.mp3".
        // We append the sub-folder to the M3U's parent doc ID and look there.
        if (m3uParentDocId != null && !entryPath.startsWith('/') && entryPath.contains('/')) {
            val relativeDir = entryPath.substringBeforeLast('/')
            val resolvedParent = "$m3uParentDocId/$relativeDir"
            audioByDirAndName[resolvedParent]?.get(entryFilename)?.let { return it }
        }

        // Strategy 3 — filename match anywhere in the library, ignoring directory.
        audioByFilename[entryFilename]?.let { return it }

        return null
    }

    /**
     * Safely extracts the SAF document ID from a content URI string, returning null
     * for anything that is not a proper SAF document URI so the caller never crashes.
     *
     * @param uri The URI string stored in [Audio.uri] or used as a playlist file key.
     * @return The document ID (e.g. "primary:Music/song.mp3"), or null.
     */
    private fun docIdOf(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return runCatching { DocumentsContract.getDocumentId(uri.toUri()) }.getOrNull()
    }

    /**
     * Builds a minimal [Audio] placeholder for a track referenced in an M3U file
     * that could not be matched to any existing database row.
     *
     * The placeholder is flagged as unavailable so it stays invisible in normal library
     * views. It acts purely as a position-holder inside the playlist until the real file
     * is scanned (or until the M3U is rescanned and the entry resolves properly).
     *
     * @param rawPath      Normalized path string from the M3U entry.
     * @param displayTitle Title from the M3U's EXTINF line, or null if absent.
     * @return A ready-to-insert placeholder [Audio].
     */
    private fun buildGhostAudio(rawPath: String, displayTitle: String?): Audio {
        val file = File(rawPath)
        return Audio().apply {
            name = file.name
            setTitle(displayTitle ?: file.nameWithoutExtension)
            uri = rawPath
            mimeType = file.extension.lowercase()
            hash = hashPath(rawPath)
            size = 0L
            duration = 0L
            bitrate = 0L
            samplingRate = 0L
            bitPerSample = 0L
            dateAdded = System.currentTimeMillis()
            dateModified = 0L
            dateTaken = 0L
            numTracks = "0"
            isAvailable = false
        }
    }

    /**
     * Produces a stable 64-bit hash for a raw path string used as a ghost entry's key.
     *
     * The "ghost:" prefix keeps these values in a completely separate space from the
     * XXHash64 fingerprints that the audio scanner generates for real tracks, so a
     * ghost entry can never accidentally collide with a legitimate library entry.
     *
     * @param path The path string to hash.
     * @return A 64-bit value suitable for [Audio.hash].
     */
    private fun hashPath(path: String): Long {
        val digest = MessageDigest.getInstance("MD5")
            .digest("ghost:$path".toByteArray(Charsets.UTF_8))
        return ByteBuffer.wrap(digest).long
    }
}
