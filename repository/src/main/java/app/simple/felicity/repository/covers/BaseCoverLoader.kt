package app.simple.felicity.repository.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import app.simple.felicity.preferences.SAFPreferences
import app.simple.felicity.repository.covers.BaseCoverLoader.loadEmbeddedArtwork
import app.simple.felicity.repository.covers.BaseCoverLoader.loadExternalArtwork
import app.simple.felicity.repository.covers.BaseCoverLoader.loadExternalArtworkSAF
import app.simple.felicity.repository.metadata.TagLibBridge
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Core cover art loading functionality shared across all media types.
 * Think of this as the Swiss Army knife for finding album art — it tries
 * every trick in the book before giving up.
 *
 * @author Hamza417
 */
internal object BaseCoverLoader {
    private const val TAG = "BaseCoverLoader"
    private const val ALBUM_ART_PATH = "/no_album_art.png"

    /**
     * Pre-compiled regex for sanitizing names into filesystem-safe filenames.
     * Compiled once at class initialization to avoid repeated compilation overhead.
     */
    private val SANITIZE_REGEX = Regex("[^a-zA-Z0-9.-]")

    /**
     * Caches the external artwork bitmap (or a "we already looked and found nothing" marker)
     * keyed by folder path or SAF parent document ID. This prevents us from scanning the
     * same folder over and over for every track inside an album — which was the main culprit
     * behind the 2-second load times.
     *
     * The value is a [Result] wrapping the bitmap so we can distinguish between
     * "not cached yet" (absent) and "cached as empty" (present with null inside).
     */
    private val externalArtworkCache = ConcurrentHashMap<String, Result<Bitmap?>>()

    /**
     * Clears the in-memory external artwork cache. Call this when the user adds or removes
     * music folders so stale entries don't linger.
     */
    fun clearExternalArtworkCache() {
        externalArtworkCache.clear()
        Log.d(TAG, "External artwork cache cleared")
    }

    /**
     * Hardcoded list of the most common album art filenames used by music rippers,
     * media players, and tagging tools. Ordered by real-world prevalence so the
     * most likely match is found with the fewest file-existence checks.
     */
    val COMMON_ARTWORK_NAMES = listOf(
            "folder.jpg",    // Most common — Windows Media Player, MusicBrainz Picard
            "cover.jpg",     // beets, MusicBrainz Picard alternate
            "front.jpg",     // EAC, dBpoweramp
            "album.jpg",     // iTunes-style rips
            "albumart.jpg",  // Winamp, foobar2000
            "folder.png",
            "cover.png",
            "front.png",
            "album.png",
            "albumart.png",
            "Folder.jpg",    // Windows Explorer thumbnail convention
            "AlbumArt.jpg"   // Windows Media Player legacy
    )

    /**
     * Looks for common image files (folder.jpg, cover.jpg, etc.) directly inside
     * a given directory. Only works when we have an actual [File] reference —
     * use [loadExternalArtworkSAF] instead when dealing with content URIs.
     *
     * @param directory Directory to search for artwork files.
     * @param customNames Optional extra filenames to check (e.g., album or artist name variants).
     * @return Bitmap of the first matching artwork file, or null if none is found.
     */
    fun loadExternalArtwork(directory: File, customNames: List<String> = emptyList()): Bitmap? {
        val cacheKey = directory.absolutePath

        // Return a fresh copy of the cached bitmap so Glide can freely recycle the
        // copy it receives without corrupting the master we're holding in the cache.
        // If the cached bitmap was already recycled (Glide put it back in its pool),
        // we evict the stale entry and fall through to re-scan the folder.
        externalArtworkCache[cacheKey]?.let { cached ->
            val bitmap = cached.getOrNull() ?: return null
            if (!bitmap.isRecycled) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            externalArtworkCache.remove(cacheKey)
        }

        val allNames = COMMON_ARTWORK_NAMES + customNames

        for (filename in allNames) {
            val artFile = File(directory, filename)
            if (artFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(artFile.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Found external art: ${artFile.name}")
                        externalArtworkCache[cacheKey] = Result.success(bitmap)
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode external art: ${artFile.name}", e)
                }
            }
        }

        // Store a null result so we don't re-scan this folder for future tracks.
        externalArtworkCache[cacheKey] = Result.success(null)
        return null
    }

    /**
     * SAF-aware version of [loadExternalArtwork]. Given a content URI for an audio
     * file, it figures out the parent folder's document ID, then asks the system
     * for a list of that folder's children and looks for any of our known artwork
     * filenames among them. If a match is found, we open it via the content resolver
     * and decode it into a bitmap.
     *
     * This works because SAF uses document IDs that look like paths
     * (e.g. "primary:Music/Album/song.mp3"), so we can strip the last segment to
     * arrive at the parent ("primary:Music/Album") and query its children.
     *
     * @param context Android context needed to query the content resolver.
     * @param audioUri The SAF content URI of the audio file whose folder we want to inspect.
     * @param customNames Optional extra filenames to look for (e.g., album name variants).
     * @return Bitmap from the first matching image file found, or null if none exists.
     */
    fun loadExternalArtworkSAF(context: Context, audioUri: Uri, customNames: List<String> = emptyList()): Bitmap? {
        val docId = try {
            DocumentsContract.getDocumentId(audioUri)
        } catch (e: Exception) {
            Log.w(TAG, "Could not get document ID from URI: $audioUri", e)
            return null
        }

        // Strip the last path segment to get the parent folder's document ID.
        // For "primary:Music/Album/song.mp3" this gives "primary:Music/Album".
        val parentDocId = docId.substringBeforeLast('/', missingDelimiterValue = "")
        if (parentDocId.isEmpty()) {
            Log.w(TAG, "Could not determine parent document ID for: $docId")
            return null
        }

        return loadExternalArtworkSAFByDocId(context, parentDocId, customNames)
    }

    /**
     * The actual SAF artwork scanner — given a parent folder's document ID, it directly
     * probes for each known artwork filename in that folder without any cursor traversal.
     *
     * SAF document IDs for the standard Android storage provider are path-based
     * (e.g. "primary:Music/Album"), so we can construct a child doc ID by simply
     * appending the filename ("primary:Music/Album/folder.jpg") and try to open it.
     * If the file isn't there, [openInputStream] returns null and we move on.
     * No listing, no child queries — pure hit-or-miss per filename.
     *
     * Both [loadExternalArtworkSAF] (which derives the parent doc ID from an audio URI)
     * and [FolderCover] (which already has the folder doc ID directly) funnel through here
     * so the logic and cache live in exactly one place.
     *
     * @param context Android context needed for the content resolver.
     * @param parentDocId SAF document ID of the folder to inspect (e.g. "primary:Music/Album").
     * @param customNames Optional extra filenames to look for beyond the common defaults.
     * @return Bitmap from the first matching image, or null if the folder has no artwork.
     */
    fun loadExternalArtworkSAFByDocId(context: Context, parentDocId: String, customNames: List<String> = emptyList()): Bitmap? {
        // Same recycled-bitmap guard as the file-path variant: return a fresh copy and
        // evict the entry if Glide already recycled the cached master.
        externalArtworkCache[parentDocId]?.let { cached ->
            val bitmap = cached.getOrNull()
            if (bitmap == null) return null
            if (!bitmap.isRecycled) return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
            externalArtworkCache.remove(parentDocId)
        }

        // Find the tree URI whose root covers our parent document ID.
        val treeUri = SAFPreferences.getTreeUris().firstNotNullOfOrNull { uriStr ->
            val candidate = uriStr.toUri()
            val rootDocId = try {
                DocumentsContract.getTreeDocumentId(candidate)
            } catch (e: Exception) {
                return@firstNotNullOfOrNull null
            }
            if (parentDocId.startsWith(rootDocId)) candidate else null
        }

        if (treeUri == null) {
            Log.w(TAG, "No matching tree URI found for document: $parentDocId")
            externalArtworkCache[parentDocId] = Result.success(null)
            return null
        }

        // Probe each known filename directly — no child listing needed.
        // We build "parentDocId/filename.jpg", convert it to a URI, and try to open it.
        // If the file isn't there the stream will be null, and we just try the next name.
        for (filename in COMMON_ARTWORK_NAMES + customNames) {
            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$parentDocId/$filename")
            val bitmap = try {
                context.contentResolver.openInputStream(childUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                // File doesn't exist or can't be read — not an error worth logging every time.
                null
            }

            if (bitmap != null) {
                Log.d(TAG, "Found SAF external art: $filename")
                externalArtworkCache[parentDocId] = Result.success(bitmap)
                return bitmap
            }
        }

        // Nothing found — cache the negative result to skip this folder next time.
        externalArtworkCache[parentDocId] = Result.success(null)
        return null
    }

    /**
     * The single entry point for pulling embedded artwork out of any audio file.
     * Internally it tries TagLib first (better WAV/FLAC/APE support), and only
     * falls back to MediaMetadataRetriever when TagLib comes up empty. Callers
     * don't need to know which extractor was actually used.
     *
     * @param context Android context needed to open the file for both extractors.
     * @param audioUri The URI or file path of the audio file.
     * @return Bitmap of the embedded artwork, or null if neither extractor found one.
     */
    fun loadEmbeddedArtwork(context: Context, audioUri: Uri): Bitmap? {
        return loadEmbeddedArtworkViaTagLib(context, audioUri)
            ?: loadEmbeddedArtworkViaMMR(context, audioUri)
    }

    /**
     * Scans through a list of song paths (or URIs — both are fine) and returns the
     * embedded artwork from the first file that actually has one. Stops early once
     * artwork is found to avoid unnecessary work.
     *
     * Each path goes through the same TagLib-first, MMR-fallback junction used by
     * [loadEmbeddedArtwork], so WAV files and other tricky formats are handled correctly.
     *
     * @param context Android context needed to open content URIs via SAF.
     * @param songPaths List of audio file paths or content URI strings to check.
     * @param maxFiles Maximum number of files to check before giving up (default: 5).
     * @return Bitmap or null if none of the checked files had embedded artwork.
     */
    fun loadEmbeddedArtworkFromPaths(context: Context, songPaths: List<String>, maxFiles: Int = 5): Bitmap? {
        for (path in songPaths.take(maxFiles)) {
            val bitmap = loadEmbeddedArtwork(context, path.toUri())
            if (bitmap != null) return bitmap
        }
        return null
    }

    /**
     * Uses TagLib (via JNI) to extract the embedded cover art. TagLib handles WAV,
     * FLAC, APE, and most other lossless formats more reliably than MMR.
     */
    private fun loadEmbeddedArtworkViaTagLib(context: Context, audioUri: Uri): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(audioUri, "r")?.use { pfd ->
                val bytes = TagLibBridge.nativeExtractArtworkFromFd(pfd.fd)
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) Log.d(TAG, "TagLib extracted artwork from: $audioUri")
                    bitmap
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TagLib failed to extract artwork from: $audioUri", e)
            null
        }
    }

    /**
     * Uses MediaMetadataRetriever as a fallback for formats that TagLib missed,
     * such as certain MP4/AAC variants where the system decoder has an edge.
     */
    private fun loadEmbeddedArtworkViaMMR(context: Context, audioUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, audioUri)
            val embeddedPicture = retriever.embeddedPicture
            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
                if (bitmap != null) Log.d(TAG, "MMR extracted artwork from: $audioUri")
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "MMR failed to extract artwork from: $audioUri", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing MediaMetadataRetriever", e)
            }
        }
    }

    /**
     * Generate custom artwork filename variants based on a name.
     * Useful for looking up folder.jpg alternatives named after the album or artist.
     *
     * @param name Name to generate variants for (e.g., album or artist name).
     * @return List of filename variants.
     */
    fun generateCustomArtworkNames(name: String?): List<String> {
        if (name.isNullOrEmpty()) return emptyList()

        val sanitizedName = name.replace(SANITIZE_REGEX, "_")

        return listOf(
                "$sanitizedName.jpg", "$sanitizedName.png",
                "${sanitizedName}_cover.jpg", "${sanitizedName}_cover.png",
                "${sanitizedName}_front.jpg", "${sanitizedName}_back.png",
        )
    }

    /**
     * Returns the built-in placeholder artwork when no cover art can be found anywhere.
     * It's not glamorous, but it's better than a crash or a blank space.
     */
    fun loadEmptyAudioCover(): Bitmap {
        val stream = BaseCoverLoader::class.java.getResourceAsStream(ALBUM_ART_PATH)
        return BitmapFactory.decodeStream(stream)
    }
}
