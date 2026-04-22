package app.simple.felicity.repository.repositories

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import app.simple.felicity.repository.metadata.LyricsMetaHelper
import app.simple.felicity.repository.models.LrcLibResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one-stop shop for anything lyrics-related: fetching from the internet,
 * saving alongside the song, and loading them back when the song plays.
 *
 * All sidecar files (.lrc and .txt) live right next to their audio file inside
 * the same SAF tree the user granted access to. This means no private cache
 * directories, no filename length limits, and the sidecars are visible to other
 * apps — just like you'd expect a "normal" file manager to behave.
 *
 * All I/O goes through [DocumentsContract] + [android.content.ContentResolver]
 * so the code works for any document provider, not just local storage.
 *
 * @author Hamza417
 */
@Singleton
class LrcRepository @Inject constructor(
        @param:ApplicationContext private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Derives the URI of a sidecar file in O(1) — no directory listing, no cursor,
     * no round-trips to the document provider.
     *
     * SAF document IDs for external storage follow the pattern
     * "primary:Music/folder/song.mp3", so replacing the extension gives us the
     * exact document ID of the sibling file. We then build a proper tree-document
     * URI from that ID and we are done.
     *
     * This is the fast path. Opening an input/output stream on the resulting URI
     * will fail if the file does not exist yet, which is how we detect absence.
     *
     * @param audioUri  The full SAF content URI of the audio file.
     * @param extension The desired sidecar extension, without the dot (e.g. "lrc").
     * @return A URI pointing to where the sidecar lives (or should live), or null
     *         when the input is not a valid SAF tree document URI.
     */
    private fun siblingUri(audioUri: String, extension: String): Uri? {
        return try {
            val uri = audioUri.toUri()
            if (!DocumentsContract.isDocumentUri(context, uri)) return null

            val treeDocId = DocumentsContract.getTreeDocumentId(uri) ?: return null
            val docId = DocumentsContract.getDocumentId(uri)

            // Swap the audio extension for the sidecar extension in the document ID.
            val siblingDocId = docId.substringBeforeLast('.') + ".$extension"

            val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority!!, treeDocId)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, siblingDocId)
        } catch (e: Exception) {
            Log.w(TAG, "Could not derive sibling URI for .$extension: ${e.message}")
            null
        }
    }

    /**
     * Returns true when the sidecar with [extension] already exists on disk.
     *
     * We query just one column on the derived URI — the document provider returns
     * an empty cursor (or throws) when the file is absent, and a one-row cursor
     * when it is present. This is much cheaper than listing the whole directory.
     */
    private fun siblingExists(audioUri: String, extension: String): Boolean {
        val uri = siblingUri(audioUri, extension) ?: return false
        return try {
            context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null
            )?.use { cursor -> cursor.count > 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Writes [content] to the sidecar file that sits next to [audioUri].
     *
     * If the file already exists we open it with "wt" (write-truncate) mode so
     * the old content is replaced in-place — no duplicate copies, no "(1)" suffix
     * nonsense. If it does not exist yet we create it first using
     * [DocumentsContract.createDocument] with MIME type "application/octet-stream"
     * so the document provider stores the file under exactly the name we give it
     * (using "text/plain" causes some providers to silently append ".txt").
     *
     * @param audioUri  The content URI of the audio file.
     * @param extension Sidecar extension without the dot (e.g. "lrc").
     * @param content   Text to write into the sidecar.
     * @return The URI of the written sidecar, or null if something went wrong.
     */
    private fun writeSibling(audioUri: String, extension: String, content: String): Uri? {
        return try {
            val targetUri = siblingUri(audioUri, extension) ?: return null

            if (siblingExists(audioUri, extension)) {
                // File is already there — just truncate and overwrite, no need to create.
                context.contentResolver.openOutputStream(targetUri, "wt")?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Sidecar .$extension overwritten for: $audioUri")
                targetUri
            } else {
                // File doesn't exist yet — create it, then write.
                val audioUri2 = audioUri.toUri()
                val treeDocId = DocumentsContract.getTreeDocumentId(audioUri2) ?: return null
                val docId = DocumentsContract.getDocumentId(audioUri2)
                val parentDocId = docId.substringBeforeLast('/', missingDelimiterValue = "")
                if (parentDocId.isEmpty()) return null

                val treeUri = DocumentsContract.buildTreeDocumentUri(audioUri2.authority!!, treeDocId)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                val siblingName = docId.substringAfterLast('/').substringBeforeLast('.') + ".$extension"

                // "application/octet-stream" ensures the provider stores the file with
                // exactly the display name we specified — no extra extensions appended.
                val newUri = DocumentsContract.createDocument(
                        context.contentResolver, parentUri, MIME_SIDECAR, siblingName
                ) ?: return null

                context.contentResolver.openOutputStream(newUri, "wt")?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Sidecar .$extension created alongside audio: $siblingName")
                newUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not write .$extension sidecar for $audioUri", e)
            null
        }
    }

    /**
     * Deletes the sidecar with [extension] sitting next to [audioUri].
     * Quietly does nothing if the sidecar does not exist yet.
     */
    private fun deleteSibling(audioUri: String, extension: String) {
        try {
            if (!siblingExists(audioUri, extension)) {
                Log.d(TAG, "No .$extension sidecar to delete for: $audioUri")
                return
            }
            val uri = siblingUri(audioUri, extension) ?: return
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
            if (deleted) {
                Log.d(TAG, ".$extension sidecar deleted for: $audioUri")
            } else {
                Log.e(TAG, "deleteDocument returned false for .$extension sidecar of: $audioUri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting .$extension sidecar for $audioUri", e)
        }
    }

    /**
     * Reads the text content of a sidecar file sitting next to [audioUri].
     * Returns null when the sidecar does not exist or is blank.
     */
    private fun readSibling(audioUri: String, extension: String): String? {
        return try {
            val uri = siblingUri(audioUri, extension) ?: return null
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText().takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            // A FileNotFoundException here just means the sidecar doesn't exist yet — totally normal.
            null
        }
    }

    /**
     * Search for available lyrics for a track.
     * Returns a list of all available lyrics matches from LrcLib.
     *
     * @param trackName  The title of the song.
     * @param artistName The artist's name.
     * @return List of [LrcLibResponse] objects containing synced lyrics, or empty list if none found.
     */
    suspend fun searchLyrics(trackName: String, artistName: String): Result<List<LrcLibResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host("lrclib.net")
                    .addPathSegment("api")
                    .addPathSegment("search")
                    .addQueryParameter("track_name", trackName)
                    .addQueryParameter("artist_name", artistName)

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                                IOException("Failed to search lyrics: ${response.code}")
                        )
                    }

                    val responseBody = response.body.string()

                    val listType = object : TypeToken<List<LrcLibResponse>>() {}.type
                    val results: List<LrcLibResponse> = gson.fromJson(responseBody, listType)

                    // Keep only entries that actually have synced lyrics — plain text is handled separately.
                    val filteredResults = results.filter { !it.syncedLyrics.isNullOrBlank() }
                    return@withContext Result.success(filteredResults)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching lyrics", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Fetch specific lyrics by LrcLib ID.
     *
     * @param lrcId The ID of the lyrics entry on LrcLib.
     * @return The [LrcLibResponse] object, or null if not found.
     */
    suspend fun fetchLyricsById(lrcId: Int): Result<LrcLibResponse?> {
        return withContext(Dispatchers.IO) {
            try {
                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host("lrclib.net")
                    .addPathSegment("api")
                    .addPathSegment("get")
                    .addPathSegment(lrcId.toString())

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                                IOException("Failed to fetch lyrics: ${response.code}")
                        )
                    }

                    val responseBody = response.body.string()
                    val result: LrcLibResponse = gson.fromJson(responseBody, LrcLibResponse::class.java)
                    return@withContext Result.success(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching lyrics by ID", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Saves [lrcContent] as a `.lrc` sidecar file next to the audio file in the SAF tree.
     *
     * @param lrcContent The LRC text to save.
     * @param audioUri   The content URI of the audio file.
     * @return [Result] wrapping the URI of the written sidecar, or the exception on failure.
     */
    suspend fun saveLrcToFile(lrcContent: String, audioUri: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            val saved = writeSibling(audioUri, "lrc", lrcContent)
            if (saved != null) {
                Result.success(saved)
            } else {
                Result.failure(IOException("Could not create .lrc sidecar alongside: $audioUri"))
            }
        }
    }

    /**
     * Saves [textContent] as a `.txt` sidecar file next to the audio file in the SAF tree.
     *
     * @param textContent The plain lyrics text to save.
     * @param audioUri    The content URI of the audio file.
     * @return [Result] wrapping the URI of the written sidecar, or the exception on failure.
     */
    suspend fun saveTxtToFile(textContent: String, audioUri: String): Result<Uri> {
        return withContext(Dispatchers.IO) {
            val saved = writeSibling(audioUri, "txt", textContent)
            if (saved != null) {
                Result.success(saved)
            } else {
                Result.failure(IOException("Could not create .txt sidecar alongside: $audioUri"))
            }
        }
    }

    /**
     * Loads LRC content for [audioUri] by checking three sources in order:
     *
     * 1. A `.lrc` sidecar file sitting next to the audio file in the same SAF directory.
     * 2. Lyrics embedded directly in the audio file's tags (USLT, ©lyr, Vorbis LYRICS, etc.)
     *    — if found they are also written as a sidecar so the next load skips the tag scan.
     *
     * @param audioUri The content URI of the audio file.
     * @return [Result] wrapping the LRC string if found, null if none exists, or
     *         the exception if something went wrong.
     */
    suspend fun loadLrcFromFile(audioUri: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                // Check 1: sibling .lrc sidecar in the same SAF directory — instant, no scan.
                val lrcContent = readSibling(audioUri, "lrc")
                if (!lrcContent.isNullOrBlank()) {
                    Log.d(TAG, "Loaded .lrc sidecar for: $audioUri")
                    return@withContext Result.success(lrcContent)
                }

                // Check 2: lyrics baked into the audio file's tags (USLT, ©lyr, Vorbis, etc.).
                // If we find embedded lyrics we cache them as a sidecar right away so future
                // plays skip this tag scan and go straight to Check 1 above.
                val embedded = LyricsMetaHelper.extractEmbeddedLyrics(context, audioUri)
                if (!embedded.isNullOrBlank()) {
                    Log.d(TAG, "Found embedded lyrics in tags for: $audioUri — caching as .lrc sidecar.")
                    writeSibling(audioUri, "lrc", embedded)
                    return@withContext Result.success(embedded)
                }

                return@withContext Result.success(null)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading LRC for URI: $audioUri", e)
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Loads plain-text lyrics from the `.txt` sidecar next to [audioUri].
     *
     * @param audioUri The content URI of the audio file.
     * @return [Result] wrapping the plain-text string if found, null if none exists.
     */
    suspend fun loadTxtFromFile(audioUri: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            try {
                Result.success(readSibling(audioUri, "txt"))
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TXT lyrics for URI: $audioUri", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Returns true if a `.lrc` sidecar exists next to the audio file in the SAF tree.
     *
     * @param audioUri The content URI of the audio file.
     */
    fun lrcFileExists(audioUri: String): Boolean {
        return siblingExists(audioUri, "lrc")
    }

    /**
     * Deletes both the `.lrc` and `.txt` sidecar files sitting next to [audioUri].
     * If either sidecar is absent, that deletion is silently skipped.
     *
     * @param audioUri The content URI of the audio file.
     */
    fun deleteLrcFile(audioUri: String) {
        deleteSibling(audioUri, "lrc")
        deleteSibling(audioUri, "txt")
    }

    companion object {
        private const val TAG = "LrcRepository"
        private const val USER_AGENT = "Felicity Music Player (https://github.com/Hamza417/Felicity)"

        /**
         * Using "application/octet-stream" instead of "text/plain" when creating new sidecar
         * documents is crucial — some document providers (looking at you, ExternalStorageProvider)
         * will append ".txt" to the display name when the MIME type is text/plain, turning
         * "song.lrc" into "song.lrc.txt". With octet-stream the provider stores the file under
         * exactly the display name we asked for, no surprises.
         */
        private const val MIME_SIDECAR = "application/octet-stream"

        /**
         * Deletes the `.lrc` and `.txt` sidecars for [audioUri] without a Hilt-injected
         * instance — handy in one-off cleanup flows like the "delete song" confirmation
         * dialog where wiring up the full repository would be overkill.
         *
         * @param context  Any Android context — only the [Context.contentResolver] is used.
         * @param audioUri The content URI of the audio file whose sidecars should be removed.
         */
        fun deleteSidecarsStatic(context: Context, audioUri: String) {
            // A throw-away instance is fine — LrcRepository holds no mutable state.
            val repo = LrcRepository(context)
            repo.deleteSibling(audioUri, "lrc")
            repo.deleteSibling(audioUri, "txt")
        }
    }
}
