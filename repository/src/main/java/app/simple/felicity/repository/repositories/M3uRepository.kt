package app.simple.felicity.repository.repositories

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import app.simple.felicity.core.m3u.M3uParser
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.repository.models.PlaylistSongCrossRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jpountz.xxhash.XXHashFactory
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles importing M3U and M3U8 playlist files into the app's database.
 *
 * <p>Given a content URI pointing to an M3U file, this repository reads the file,
 * parses every track entry, resolves relative paths against the M3U file's own
 * directory, and creates a new playlist. Tracks that are already in the library
 * are linked by their existing hash. Tracks that are not yet in the library get
 * a lightweight placeholder entry so the playlist still keeps every slot the
 * original author intended — even for files that haven't been scanned yet.</p>
 *
 * <p>Placeholder entries are marked {@code isAvailable = false} so they won't
 * show up in normal library views. They will be cleaned up automatically by the
 * library scanner the next time it reconciles the database with the file system.</p>
 *
 * @author Hamza417
 */
@Singleton
class M3uRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    /**
     * Summary of what happened after a successful M3U import.
     *
     * @param playlistName The name given to the new playlist (from {@code #PLAYLIST}
     *                     tag or the M3U filename, whichever was available).
     * @param totalTracks  Total number of track entries found in the M3U file.
     * @param foundTracks  How many of those tracks were already in the library and
     *                     got linked directly — the rest got placeholder entries.
     */
    data class ImportResult(
            val playlistName: String,
            val totalTracks: Int,
            val foundTracks: Int
    )

    private val database by lazy { AudioDatabase.getInstance(context) }

    /**
     * Reads an M3U file from the given URI, parses it, and creates a new playlist
     * containing all referenced tracks. Relative paths in the file are resolved
     * relative to the directory that contains the M3U file itself.
     *
     * <p>This function always runs on the IO dispatcher — it does not need to be
     * called from a background thread by the caller.</p>
     *
     * @param uri The content or file URI of the M3U file chosen by the user.
     * @return A [Result] wrapping an [ImportResult] on success, or an exception on failure.
     */
    suspend fun importFromUri(uri: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            // Read the full M3U content through the content resolver. We trust that
            // the user picked a reasonably sized playlist file, not a terabyte stream.
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Could not open the selected M3U file for reading.")

            // Figure out where the M3U file lives so we can resolve relative paths.
            val baseDir = resolveBaseDirectory(uri)
            Log.d(TAG, "M3U base directory resolved to: $baseDir")

            // Derive the playlist name from the file name, stripping the extension.
            val fileName = resolveFileName(uri) ?: "Imported Playlist"
            val fileBaseName = fileName.substringBeforeLast('.').ifBlank { fileName }

            // Parse the M3U content into a structured playlist object.
            val m3uPlaylist = M3uParser.parse(content)
            Log.d(TAG, "Parsed ${m3uPlaylist.entries.size} entries from '$fileName'")

            // Prefer the #PLAYLIST name if the file provided one; fall back to filename.
            val playlistName = m3uPlaylist.name?.takeIf { it.isNotBlank() } ?: fileBaseName

            val audioDao = database.audioDao() ?: error("AudioDao is not available.")
            val playlistDao = database.playlistDao()

            // Create the new playlist row and get its auto-generated ID.
            val playlistId = playlistDao.insertPlaylist(
                    Playlist(
                            name = playlistName,
                            dateCreated = System.currentTimeMillis(),
                            dateModified = System.currentTimeMillis()
                    )
            )
            Log.d(TAG, "Created playlist '$playlistName' with ID $playlistId")

            var foundCount = 0
            val crossRefs = mutableListOf<PlaylistSongCrossRef>()

            // Process every track entry from the M3U file, one at a time.
            for ((index, entry) in m3uPlaylist.entries.withIndex()) {
                val absolutePath = resolveAbsolutePath(entry.path, baseDir)

                // Check whether this track is already in our library.
                val existingAudio = audioDao.getAudioByPath(absolutePath)

                val audioHash = if (existingAudio != null) {
                    // Great — we already know this one. Link directly using its real hash.
                    foundCount++
                    Log.d(TAG, "Found existing track for: $absolutePath")
                    existingAudio.hash
                } else {
                    // The track is not in the library yet. Insert a placeholder so the
                    // playlist can hold its spot until the library scanner gets to it.
                    val ghost = buildGhostAudio(absolutePath, entry.title)
                    val insertedRowId = audioDao.insertOrIgnore(ghost)
                    if (insertedRowId == -1L) {
                        Log.w(TAG, "Ghost insert ignored for: $absolutePath (hash collision, using computed hash)")
                    } else {
                        Log.d(TAG, "Inserted ghost entry for: $absolutePath")
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

            // Add all the tracks to the playlist in one shot.
            playlistDao.addSongsToPlaylist(crossRefs)
            playlistDao.touchModified(playlistId, System.currentTimeMillis())

            Log.d(TAG, "Import complete — $playlistName: ${crossRefs.size} tracks, $foundCount already in library.")

            ImportResult(
                    playlistName = playlistName,
                    totalTracks = m3uPlaylist.entries.size,
                    foundTracks = foundCount
            )
        }
    }

    /**
     * Converts a possibly-relative path from the M3U file into an absolute path.
     *
     * <p>Absolute paths (starting with {@code /}) are returned unchanged. Network
     * URLs containing {@code ://} are also passed through as-is. Relative paths
     * are joined with [baseDir] when available; otherwise the raw path is returned
     * and the caller will have to live with the ambiguity.</p>
     *
     * <p>Windows-style backslashes are normalized to forward slashes because M3U
     * files created on Windows sometimes sneak those in.</p>
     *
     * @param rawPath The path string as written in the M3U file.
     * @param baseDir The absolute directory that contains the M3U file, or null
     *                if we couldn't figure that out.
     * @return The best absolute path we can produce for this entry.
     */
    private fun resolveAbsolutePath(rawPath: String, baseDir: String?): String {
        // Normalize backslashes to forward slashes so the rest of the code
        // doesn't have to worry about Windows-style separators.
        val normalized = rawPath.replace('\\', '/')

        return when {
            // Already absolute — nothing to do here.
            normalized.startsWith('/') -> normalized
            // Looks like a network URL (http://, ftp://, etc.) — keep it as-is.
            normalized.contains("://") -> normalized
            // Relative path with a known base directory.
            baseDir != null -> "$baseDir/$normalized"
            // No base directory available — return the relative path unchanged.
            else -> normalized
        }
    }

    /**
     * Attempts to extract the absolute directory path of the M3U file from its URI.
     *
     * <p>For {@code file://} URIs the parent directory is read directly from the path.
     * For {@code content://} URIs from Android's {@code ExternalStorageProvider} the
     * document ID (e.g. {@code primary:Music/playlists/summer.m3u}) is decoded and
     * combined with the storage root to produce the real file system path.</p>
     *
     * @param uri The URI of the M3U file as returned by the file picker.
     * @return The absolute directory path, or null if it could not be determined.
     */
    private fun resolveBaseDirectory(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path ?: return null).parent

                "content" -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val colonIdx = docId.indexOf(':')
                    if (colonIdx < 0) return null

                    val volume = docId.substring(0, colonIdx)
                    val relativePath = docId.substring(colonIdx + 1)

                    // Map the volume label to its storage root path.
                    val storageRoot = when (volume) {
                        "primary" -> "/storage/emulated/0"
                        else -> "/storage/$volume"
                    }

                    File("$storageRoot/$relativePath").parent
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve base directory for URI: $uri", e)
            null
        }
    }

    /**
     * Pulls the file name from the URI so we can use it as a fallback playlist name.
     *
     * @param uri The URI of the M3U file.
     * @return The file name (including extension), or null if it could not be extracted.
     */
    private fun resolveFileName(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> File(uri.path ?: return null).name

                "content" -> {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val colonIdx = docId.indexOf(':')
                    val relativePath = if (colonIdx >= 0) docId.substring(colonIdx + 1) else docId
                    File(relativePath).name
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract file name from URI: $uri", e)
            null
        }
    }

    /**
     * Creates a lightweight placeholder [Audio] entry for a track that is referenced
     * in an M3U file but has not yet been scanned into the library.
     *
     * <p>The placeholder is marked {@code isAvailable = false} so it stays hidden
     * from normal library views. Its hash is derived from the file path (not file
     * contents) using a seed of {@code 0} — distinct from the {@code Integer.MAX_VALUE}
     * seed used by the real scanner — which keeps the two hash spaces well separated
     * and practically collision-free.</p>
     *
     * @param absolutePath  The resolved absolute path of the audio file.
     * @param displayTitle  Optional title from the M3U's {@code #EXTINF} tag, used
     *                      as a friendly name in case the file never shows up.
     * @return A minimally populated [Audio] ready to be inserted as a placeholder.
     */
    private fun buildGhostAudio(absolutePath: String, displayTitle: String?): Audio {
        val file = File(absolutePath)
        val audio = Audio()

        audio.setName(file.name)
        audio.setTitle(displayTitle ?: file.nameWithoutExtension)
        audio.setPath(absolutePath)
        audio.setMimeType(file.extension.lowercase())
        audio.setHash(hashPath(absolutePath))
        audio.setSize(0L)
        audio.setDuration(0L)
        audio.setBitrate(0L)
        audio.setSamplingRate(0L)
        audio.setBitPerSample(0L)
        audio.setDateAdded(System.currentTimeMillis())
        audio.setDateModified(0L)
        audio.setDateTaken(0L)
        audio.setNumTracks("0")
        // Mark it as unavailable so normal library queries skip it.
        audio.isAvailable = false

        return audio
    }

    /**
     * Produces a deterministic 64-bit hash of a file path string.
     *
     * <p>We use XXHash64 with seed {@code 0} on the UTF-8 bytes of the path.
     * The real scanner hashes file *contents* with seed {@code Integer.MAX_VALUE},
     * so these two hash spaces are effectively independent and collisions between
     * a ghost entry and a real library entry are astronomically unlikely.</p>
     *
     * @param path The absolute file path to hash.
     * @return A 64-bit hash value suitable for use as the {@code Audio.hash} field.
     */
    private fun hashPath(path: String): Long {
        val factory = XXHashFactory.fastestInstance()
        val hasher = factory.newStreamingHash64(0L)
        val bytes = path.toByteArray(Charsets.UTF_8)
        hasher.update(bytes, 0, bytes.size)
        return hasher.value
    }

    companion object {
        private const val TAG = "M3uRepository"
    }
}

