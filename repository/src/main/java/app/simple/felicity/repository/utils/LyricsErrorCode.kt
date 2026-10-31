package app.simple.felicity.repository.utils

import com.google.gson.JsonParseException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Thrown when LrcLib answers with a non-2xx HTTP status.
 *
 * @property httpCode the HTTP status code returned by the server.
 */
class LrcLibHttpException(val httpCode: Int, message: String) : IOException(message)

/**
 * Turns a lyrics-related failure into a short, stable code the UI can show to the user
 * (and the user can quote in a bug report) instead of a vague "No lyrics found".
 */
fun Throwable.toLyricsErrorCode(): String {
    return when (this) {
        is LrcLibHttpException -> "HTTP $httpCode"
        is UnknownHostException -> "NO_INTERNET"
        is ConnectException -> "CONNECTION_FAILED"
        is SocketTimeoutException -> "TIMEOUT"
        is SSLException -> "SSL_ERROR"
        is JsonParseException -> "PARSE_ERROR"
        is IOException -> "IO_ERROR"
        else -> "UNKNOWN"
    }
}
