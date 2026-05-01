package app.simple.felicity.server

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.scale
import androidx.core.net.toUri
import app.simple.felicity.R
import app.simple.felicity.core.constants.ThemeConstants
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.SongStatRepository
import app.simple.felicity.theme.managers.ThemeManager
import com.google.gson.GsonBuilder
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Local WiFi HTTP server that exposes the device music library as a fully-featured
 * single-page web application with Artists, Albums, Genres, and Songs browsing.
 *
 * Routes served:
 *
 * - `GET /`                            — Serves the bundled `index.html` web player.
 * - `GET /style.css`                   — Serves the bundled stylesheet.
 * - `GET /player.js`                   — Serves the bundled JavaScript.
 * - `GET /app-icon`                    — Serves the round launcher icon as PNG.
 * - `GET /api/theme`                   — Current accent color + dark/light mode as JSON.
 * - `GET /api/songs`                   — JSON array of all available songs.
 * - `GET /api/songs/{id}/stream`       — Byte-range-aware audio streaming.
 * - `GET /api/songs/{id}/art`          — Album art for a song, as JPEG.
 * - `GET /api/albums`                  — JSON array of all albums.
 * - `GET /api/albums/songs?name=X`     — Songs in the named album.
 * - `GET /api/artists`                 — JSON array of all artists.
 * - `GET /api/artists/songs?name=X`    — Songs by the named artist.
 * - `GET /api/genres`                  — JSON array of all genres.
 * - `GET /api/genres/songs?name=X`     — Songs in the named genre.
 * - `POST /api/songs/{id}/played`      — Records a play event in the app database.
 *
 * @param context             Application context for asset/resource access.
 * @param port                TCP port the server binds to.
 * @param songStatRepository  Repository used to persist web-initiated play events.
 *
 * @author Hamza417
 */
class MusicHttpServer(
        private val context: Context,
        port: Int,
        private val songStatRepository: SongStatRepository
) : NanoHTTPD(port) {

    private val gson = GsonBuilder().create()

    private val audioDatabase: AudioDatabase by lazy {
        AudioDatabase.getInstance(context)
    }

    /** ID → Audio map, rebuilt whenever the songs endpoint is queried. */
    private var audioCache: Map<Long, Audio> = emptyMap()

    /** Cached round launcher icon PNG bytes, generated once on first request. */
    private var iconBytes: ByteArray? = null

    override fun serve(session: IHTTPSession): Response {
        return try {
            when (session.method) {
                Method.POST -> handlePost(session)
                Method.DELETE -> handleDelete(session)
                else -> handleGet(session)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ${session.method} ${session.uri}", e)
            newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Internal server error: ${e.message}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun handleGet(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri == "/" || uri == "/index.html" -> serveAsset("server/index.html", MIME_HTML)
            uri == "/app-icon" -> serveAppIcon()
            uri == "/api/theme" -> serveTheme()
            uri == "/api/songs" -> serveSongList()
            uri == "/api/albums" -> serveAlbums()
            uri == "/api/albums/songs" -> serveAlbumSongs(session.parms["name"] ?: "")
            uri == "/api/artists" -> serveArtists()
            uri == "/api/artists/songs" -> serveArtistSongs(session.parms["name"] ?: "")
            uri == "/api/genres" -> serveGenres()
            uri == "/api/genres/songs" -> serveGenreSongs(session.parms["name"] ?: "")
            STREAM_REGEX.matches(uri) -> {
                val id = STREAM_REGEX.find(uri)!!.groupValues[1].toLong()
                streamAudio(id, session)
            }
            ART_REGEX.matches(uri) -> {
                val id = ART_REGEX.find(uri)!!.groupValues[1].toLong()
                serveAlbumArt(id)
            }
            STATIC_REGEX.matches(uri) -> {
                val filename = uri.removePrefix("/")
                val mimeType = if (filename.endsWith(".css")) MIME_CSS else MIME_JS
                try {
                    serveAsset("server/$filename", mimeType)
                } catch (_: Exception) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun handlePost(session: IHTTPSession): Response {
        val uri = session.uri
        return if (PLAYED_REGEX.matches(uri)) {
            val id = PLAYED_REGEX.find(uri)!!.groupValues[1].toLongOrNull()
                ?: return badRequest("Invalid song ID")
            val audio = findAudio(id) ?: return notFound("Song not found")
            runBlocking { songStatRepository.recordPlay(audio.hash) }
            newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK")
        } else {
            newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method not allowed"
            )
        }
    }

    /**
     * Handles `DELETE /api/songs/{id}` by permanently removing the audio file from disk
     * and evicting it from the in-memory cache.
     *
     * This is a destructive, irreversible operation. The Android MediaStore will reflect
     * the change on its next scan.
     */
    private fun handleDelete(session: IHTTPSession): Response {
        val uri = session.uri
        if (!DELETE_REGEX.matches(uri)) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        val id = DELETE_REGEX.find(uri)!!.groupValues[1].toLongOrNull()
            ?: return badRequest("Invalid song ID")
        val audio = findAudio(id) ?: return notFound("Song not found")
        val audioUri = audio.uri.toUri()
        return try {
            // Ask the system to delete the file through its own content provider.
            // This works for both MediaStore and SAF URIs, so no raw File tricks needed.
            val deleted = context.contentResolver.delete(audioUri, null, null)
            if (deleted > 0) {
                audioCache = audioCache - id
                Log.i(TAG, "Deleted audio with URI: ${audio.uri}")
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
            } else {
                Log.w(TAG, "ContentResolver reported 0 rows deleted for URI: ${audio.uri}")
                newFixedLengthResponse(
                        Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not delete file"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete URI: ${audio.uri}", e)
            newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not delete file"
            )
        }
    }

    private fun serveAsset(path: String, mimeType: String): Response {
        val text = context.assets.open(path).bufferedReader().use { it.readText() }
        return newFixedLengthResponse(Response.Status.OK, mimeType, text)
    }

    /**
     * Serves the app icon as a PNG image.
     *
     * Prefers the bundled `server/app_icon.png` asset (if the user has placed one), and
     * falls back to decoding the round launcher icon from mipmap resources when the asset
     * is not present. Result is cached after the first request.
     */
    private fun serveAppIcon(): Response {
        if (iconBytes == null) {
            iconBytes = try {
                context.assets.open("server/app_icon.png").use { it.readBytes() }
            } catch (_: Exception) {
                val src = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round)
                val out = ByteArrayOutputStream()
                src.scale(128, 128) /* scale to 128×128 */
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }
        val bytes = iconBytes!!
        return newFixedLengthResponse(
                Response.Status.OK, "image/png",
                ByteArrayInputStream(bytes), bytes.size.toLong()
        )
    }

    private fun serveSongList(): Response {
        val songs = audioDatabase.audioDao()?.getAllAudioList() ?: emptyList()
        audioCache = songs.associateBy { it.id }
        return jsonOk(songs.map { it.toMap() })
    }

    private fun serveAlbums(): Response {
        val albums = cachedSongs()
            .filter { !it.album.isNullOrBlank() }
            .groupBy { it.album!! }
            .map { (name, group) ->
                mapOf(
                        "name" to name,
                        "artist" to (group.firstOrNull()?.artist ?: ""),
                        "songCount" to group.size,
                        "coverSongId" to (group.firstOrNull()?.id ?: 0L)
                )
            }
            .sortedBy { (it["name"] as? String ?: "").lowercase() }
        return jsonOk(albums)
    }

    private fun serveArtists(): Response {
        val artists = cachedSongs()
            .filter { !it.artist.isNullOrBlank() }
            .groupBy { it.artist!! }
            .map { (name, group) ->
                mapOf(
                        "name" to name,
                        "songCount" to group.size,
                        "albumCount" to group.mapNotNull { it.album?.takeIf { a -> a.isNotBlank() } }.distinct().size,
                        "coverSongId" to (group.firstOrNull()?.id ?: 0L)
                )
            }
            .sortedBy { (it["name"] as? String ?: "").lowercase() }
        return jsonOk(artists)
    }

    private fun serveGenres(): Response {
        val genres = cachedSongs()
            .groupBy { it.genre?.takeIf { g -> g.isNotBlank() } ?: UNKNOWN_GENRE }
            .map { (name, group) ->
                mapOf(
                        "name" to name,
                        "songCount" to group.size,
                        "coverSongId" to (group.firstOrNull()?.id ?: 0L)
                )
            }
            .sortedBy { (it["name"] as? String ?: "").lowercase() }
        return jsonOk(genres)
    }

    private fun serveAlbumSongs(name: String): Response {
        if (name.isBlank()) return badRequest("Missing 'name' parameter")
        return jsonOk(cachedSongs().filter { it.album == name }.map { it.toMap() })
    }

    private fun serveArtistSongs(name: String): Response {
        if (name.isBlank()) return badRequest("Missing 'name' parameter")
        return jsonOk(cachedSongs().filter { it.artist == name }.map { it.toMap() })
    }

    private fun serveGenreSongs(name: String): Response {
        if (name.isBlank()) return badRequest("Missing 'name' parameter")
        val target = if (name == UNKNOWN_GENRE) null else name
        val songs = cachedSongs()
            .filter { (it.genre?.takeIf { g -> g.isNotBlank() }) == target }
            .map { it.toMap() }
        return jsonOk(songs)
    }

    /**
     * Streams the audio file through the SAF ContentResolver with HTTP Range support,
     * so the browser `<audio>` element can seek to any position without downloading
     * the whole track first. Think of it as a responsible librarian — you can ask for
     * just the chapter you want instead of borrowing the whole book.
     */
    private fun streamAudio(id: Long, session: IHTTPSession): Response {
        val audio = findAudio(id) ?: return notFound("Song not found")

        // Parse the stored SAF URI string back into an Android Uri so we can ask
        // the ContentResolver to open it for us — no raw file path needed.
        val audioUri = audio.uri.toUri()
        val mimeType = audio.mimeType?.takeIf { it.isNotBlank() } ?: "audio/*"
        val range = session.headers["range"]

        // Ask the system how large the file is without fully opening it.
        // openFileDescriptor gives us a handle we can query for the size.
        val fileLength = try {
            context.contentResolver.openFileDescriptor(audioUri, "r")?.use { it.statSize } ?: -1L
        } catch (e: Exception) {
            Log.w(TAG, "Could not stat file descriptor for URI: ${audio.uri}", e)
            -1L
        }

        return if (range != null && range.startsWith("bytes=")) {
            val parts = range.removePrefix("bytes=").split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (fileLength > 0 && parts.size > 1 && parts[1].isNotBlank()) {
                parts[1].toLong()
            } else if (fileLength > 0) {
                fileLength - 1
            } else {
                // File size unknown — just skip and stream the remainder blindly.
                Long.MAX_VALUE / 2
            }
            val length = end - start + 1

            // Open a fresh stream and skip ahead to where the browser wants to start.
            val stream: InputStream = context.contentResolver.openInputStream(audioUri)
                ?: return notFound("Could not open audio stream")
            stream.skip(start)

            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mimeType, stream, length).apply {
                addHeader("Content-Range", "bytes $start-$end/$fileLength")
                addHeader("Accept-Ranges", "bytes")
                if (fileLength > 0) addHeader("Content-Length", length.toString())
            }
        } else {
            val stream: InputStream = context.contentResolver.openInputStream(audioUri)
                ?: return notFound("Could not open audio stream")

            newFixedLengthResponse(Response.Status.OK, mimeType, stream, fileLength).apply {
                addHeader("Accept-Ranges", "bytes")
                if (fileLength > 0) addHeader("Content-Length", fileLength.toString())
            }
        }
    }

    /**
     * Resolves and serves album art for the requested song as a JPEG image.
     * Returns 404 when no artwork can be found.
     */
    private fun serveAlbumArt(id: Long): Response {
        val audio = findAudio(id) ?: return notFound("Song not found")
        val bitmap = try {
            AudioCover.load(context, audio)
        } catch (e: Exception) {
            Log.w(TAG, "Album art load failed for id=$id", e)
            null
        }
        return if (bitmap != null && !bitmap.isRecycled) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            val bytes = out.toByteArray()
            newFixedLengthResponse(
                    Response.Status.OK, "image/jpeg",
                    ByteArrayInputStream(bytes), bytes.size.toLong()
            )
        } else {
            notFound("No artwork available")
        }
    }

    private fun jsonOk(data: Any): Response {
        val json = gson.toJson(data)
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Serves the current app accent color and theme mode so the web page can
     * style itself to match the Android app exactly — no more generic blue defaults.
     *
     * Returns: `{ "accent": "#2980B9", "accentSecondary": "#5499C7", "isDark": true }`
     */
    private fun serveTheme(): Response {
        val accent = ThemeManager.accent
        val primaryHex = String.format("#%06X", 0xFFFFFF and accent.primaryAccentColor)
        val secondaryHex = String.format("#%06X", 0xFFFFFF and accent.secondaryAccentColor)

        val isDark = when (AppearancePreferences.getTheme()) {
            ThemeConstants.LIGHT_THEME,
            ThemeConstants.HIGH_CONTRAST_LIGHT,
            ThemeConstants.SOAPSTONE -> false
            ThemeConstants.FOLLOW_SYSTEM,
            ThemeConstants.DAY_NIGHT -> {
                val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                nightMask == Configuration.UI_MODE_NIGHT_YES
            }
            else -> true
        }

        val json = """{"accent":"$primaryHex","accentSecondary":"$secondaryHex","isDark":$isDark}"""
        return newFixedLengthResponse(Response.Status.OK, MIME_JSON, json).apply {
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    /**
     * Returns the current audio cache as a list, fetching from the database if empty.
     */
    private fun cachedSongs(): List<Audio> {
        if (audioCache.isEmpty()) {
            audioCache = (audioDatabase.audioDao()?.getAllAudioList() ?: emptyList())
                .associateBy { it.id }
        }
        return audioCache.values.toList()
    }

    private fun findAudio(id: Long): Audio? {
        if (audioCache.isEmpty()) {
            audioCache = (audioDatabase.audioDao()?.getAllAudioList() ?: emptyList())
                .associateBy { it.id }
        }
        return audioCache[id]
    }

    /**
     * Converts an [Audio] record to a plain map suitable for JSON serialization.
     */
    private fun Audio.toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "title" to (title?.takeIf { it.isNotBlank() } ?: name),
            "name" to name,
            "artist" to artist,
            "album" to album,
            "genre" to genre,
            "duration" to duration,
            "mimeType" to mimeType
    )

    private fun badRequest(message: String): Response =
        newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, message)

    private fun notFound(message: String): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, message)

    /**
     * Starts the server in non-daemon mode.
     *
     * @throws IOException if the server cannot bind to the configured port.
     */
    @Throws(IOException::class)
    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    companion object {
        private const val TAG = "MusicHttpServer"
        private const val MIME_HTML = "text/html; charset=utf-8"
        private const val MIME_CSS = "text/css; charset=utf-8"
        private const val MIME_JS = "application/javascript; charset=utf-8"
        private const val MIME_JSON = "application/json; charset=utf-8"
        private const val JPEG_QUALITY = 85
        private const val UNKNOWN_GENRE = "Unknown"

        private val STREAM_REGEX = Regex("/api/songs/(\\d+)/stream")
        private val ART_REGEX = Regex("/api/songs/(\\d+)/art")
        private val PLAYED_REGEX = Regex("/api/songs/(\\d+)/played")
        private val DELETE_REGEX = Regex("/api/songs/(\\d+)")

        /** Matches any top-level CSS or JS file request, e.g. `/theme.css` or `/app.js`. */
        private val STATIC_REGEX = Regex("/[\\w-]+\\.(?:css|js)")
    }
}
