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
        val allNames = COMMON_ARTWORK_NAMES + customNames

        for (filename in allNames) {
            val artFile = File(directory, filename)
            if (artFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(artFile.absolutePath)
                    if (bitmap != null) {
                        Log.d(TAG, "Found external art: ${artFile.name}")
                        return bitmap
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decode external art: ${artFile.name}", e)
                }
            }
        }

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

        // Find the tree URI that covers this document. The tree's root doc ID
        // must be a prefix of (or equal to) our document's ID.
        val treeUri = SAFPreferences.getTreeUris().firstNotNullOfOrNull { uriStr ->
            val candidate = uriStr.toUri()
            val rootDocId = try {
                DocumentsContract.getTreeDocumentId(candidate)
            } catch (e: Exception) {
                return@firstNotNullOfOrNull null
            }
            if (docId.startsWith(rootDocId)) candidate else null
        }

        if (treeUri == null) {
            Log.w(TAG, "No matching tree URI found for document: $docId")
            return null
        }

        val allNames = (COMMON_ARTWORK_NAMES + customNames).map { it.lowercase() }.toHashSet()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idxDocId = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val idxName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(idxName) ?: continue
                    if (!allNames.contains(name.lowercase())) continue

                    val childDocId = cursor.getString(idxDocId) ?: continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                    val bitmap = try {
                        context.contentResolver.openInputStream(childUri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to decode SAF artwork: $name", e)
                        null
                    }

                    if (bitmap != null) {
                        Log.d(TAG, "Found SAF external art: $name")
                        return bitmap
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query SAF children for parent: $parentDocId", e)
        }

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
