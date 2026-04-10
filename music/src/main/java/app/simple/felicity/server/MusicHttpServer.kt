package app.simple.felicity.server

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.IOException

/**
 * Local WiFi HTTP server that exposes the device music library as a browser-accessible
 * web interface and REST API.
 *
 * Routes served:
 *
 * - `GET /`                       — Serves the bundled `index.html` web player from assets.
 * - `GET /api/songs`              — Returns a JSON array of all available songs.
 * - `GET /api/songs/{id}/stream`  — Streams the audio file with HTTP Range support so the
 *                                   browser `<audio>` element can seek through the track.
 * - `GET /api/songs/{id}/art`     — Serves the album art for the given song as a JPEG.
 *
 * All request handling runs on NanoHTTPD's internal thread pool (not the main thread),
 * so blocking database reads and disk I/O are safe inside [serve].
 *
 * @param context Application context used for asset loading and album art resolution.
 * @param port    TCP port that this server will bind to.
 *
 * @author Hamza417
 */
class MusicHttpServer(
        private val context: Context,
        port: Int
) : NanoHTTPD(port) {

    private val gson = GsonBuilder().create()

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /**
     * In-memory cache that maps audio row IDs to [Audio] objects.
     * Populated on first access and refreshed every time the song list endpoint is queried.
     */
    private var audioCache: Map<Long, Audio> = emptyMap()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return try {
            route(uri, session)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request: $uri", e)
            newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Internal server error: ${e.message}"
            )
        }
    }

    /**
     * Maps the incoming URI to the appropriate handler.
     *
     * @param uri     Decoded request path.
     * @param session Full HTTP session (used for reading headers like `Range`).
     * @return A fully formed [Response].
     */
    private fun route(uri: String, session: IHTTPSession): Response {
        if (uri == "/" || uri == "/index.html") return serveIndexHtml()
        if (uri == "/api/songs") return serveSongList()

        val streamMatch = STREAM_REGEX.find(uri)
        if (streamMatch != null) {
            val id = streamMatch.groupValues[1].toLongOrNull()
                ?: return badRequest("Invalid song ID")
            return streamAudio(id, session)
        }

        val artMatch = ART_REGEX.find(uri)
        if (artMatch != null) {
            val id = artMatch.groupValues[1].toLongOrNull()
                ?: return badRequest("Invalid song ID")
            return serveAlbumArt(id)
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
    }

    private fun serveIndexHtml(): Response {
        val html = context.assets.open(ASSET_INDEX).bufferedReader().use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, html)
    }

    /**
     * Returns a JSON array of all available songs in the library.
     * Each element exposes the subset of [Audio] fields needed by the web player.
     */
    private fun serveSongList(): Response {
        val songs = audioDatabase.audioDao()?.getAllAudioList() ?: emptyList()
        audioCache = songs.associateBy { it.id }

        val payload = songs.map { audio ->
            mapOf(
                    "id" to audio.id,
                    "title" to (audio.title?.takeIf { it.isNotBlank() } ?: audio.name),
                    "name" to audio.name,
                    "artist" to audio.artist,
                    "album" to audio.album,
                    "duration" to audio.duration,
                    "mimeType" to audio.mimeType
            )
        }

        val json = gson.toJson(payload)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json).apply {
            addHeader(HEADER_CORS, "*")
        }
    }

    /**
     * Streams the raw audio file for the given song, with byte-range support.
     *
     * Responding to `Range` headers allows the browser `<audio>` element to seek
     * forward or backward without re-downloading the whole file.
     *
     * @param id      Database row ID of the requested [Audio] track.
     * @param session HTTP session carrying the optional `Range` request header.
     */
    private fun streamAudio(id: Long, session: IHTTPSession): Response {
        val audio = findAudio(id) ?: return notFound("Song not found")
        val file = audio.file

        if (!file.exists()) return notFound("Audio file not found on disk")

        val fileLength = file.length()
        val mimeType = audio.mimeType?.takeIf { it.isNotBlank() } ?: "audio/*"
        val rangeHeader = session.headers["range"]

        return if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val parts = rangeHeader.removePrefix("bytes=").split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toLong() else fileLength - 1
            val contentLength = end - start + 1

            val inputStream = FileInputStream(file).also { it.skip(start) }
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, inputStream, contentLength).apply {
                addHeader("Content-Range", "bytes $start-$end/$fileLength")
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", contentLength.toString())
            }
        } else {
            val inputStream = FileInputStream(file)
            newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, fileLength).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Content-Length", fileLength.toString())
            }
        }
    }

    /**
     * Resolves and serves the album art for the requested song as a JPEG image.
     *
     * Falls back to a 404 response when no artwork is found so the web player can
     * gracefully hide or replace the broken image.
     *
     * @param id Database row ID of the [Audio] track.
     */
    private fun serveAlbumArt(id: Long): Response {
        val audio = findAudio(id) ?: return notFound("Song not found")

        val bitmap: Bitmap? = try {
            AudioCover.load(context, audio)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load album art for id=$id", e)
            null
        }

        return if (bitmap != null && !bitmap.isRecycled) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val bytes = out.toByteArray()
            newFixedLengthResponse(
                    Response.Status.OK,
                    "image/jpeg",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong()
            )
        } else {
            notFound("No artwork available")
        }
    }

    /**
     * Looks up an [Audio] record by its row ID.
     *
     * Checks the in-memory cache first; falls back to a full database query if the
     * cache is empty (e.g. direct deep-link before the song list has been loaded).
     *
     * @param id Database row ID.
     * @return The matching [Audio] record, or `null` if not found.
     */
    private fun findAudio(id: Long): Audio? {
        if (audioCache.isEmpty()) {
            audioCache = (audioDatabase.audioDao()?.getAllAudioList() ?: emptyList())
                .associateBy { it.id }
        }
        return audioCache[id]
    }

    private fun badRequest(message: String): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, message)

    private fun notFound(message: String): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, message)

    /**
     * Starts the server in daemon mode so it does not block JVM shutdown.
     *
     * @throws IOException if the server cannot bind to the configured port.
     */
    @Throws(IOException::class)
    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    companion object {
        private const val TAG = "MusicHttpServer"
        private const val ASSET_INDEX = "server/index.html"
        private const val MIME_HTML = "text/html; charset=utf-8"
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val HEADER_CORS = "Access-Control-Allow-Origin"
        private const val JPEG_QUALITY = 85

        private val STREAM_REGEX = Regex("/api/songs/(\\d+)/stream")
        private val ART_REGEX = Regex("/api/songs/(\\d+)/art")
    }
}

