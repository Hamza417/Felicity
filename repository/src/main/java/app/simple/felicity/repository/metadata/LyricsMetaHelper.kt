package app.simple.felicity.repository.metadata

import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodySYLT
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

object LyricsMetaHelper {

    private const val TAG = "LyricsMetaHelper"

    fun extractEmbeddedLyrics(filePath: String): String? {
        return try {
            val audioFile = AudioFileIO.read(File(filePath))
            val tag = audioFile.tag ?: return null

            // STANDARD CHECK: Covers USLT (MP3), \xA9lyr (M4A), and standard LYRICS (FLAC/OGG)
            val standardLyrics = tag.getFirst(FieldKey.LYRICS)
            if (!standardLyrics.isNullOrBlank()) {
                Log.d(TAG, "Found standard lyrics in ${filePath.substringAfterLast('/')}")
                return standardLyrics
            }

            // VORBIS COMMENT FALLBACKS: Common in FLAC/OGG files tagged by 3rd party apps
            val vorbisKeys = listOf("SYNCEDLYRICS", "UNSYNCEDLYRICS")
            for (key in vorbisKeys) {
                val vorbisLyrics = tag.getFirst(key)
                if (!vorbisLyrics.isNullOrBlank()) {
                    Log.d(TAG, "Found Vorbis comment lyrics with key '$key' in ${filePath.substringAfterLast('/')}")
                    return vorbisLyrics
                }
            }

            // ID3v2 TXXX FALLBACKS: User-defined text frames in MP3s
            // Tools like MusicBee or MinLyrics sometimes embed raw LRC text into TXXX frames
            if (tag is AbstractID3v2Tag) {
                val txxxFrames = tag.getFields("TXXX")
                for (frame in txxxFrames) {
                    if (frame is AbstractID3v2Frame && frame.body is FrameBodyTXXX) {
                        val body = frame.body as FrameBodyTXXX
                        val description = body.description.uppercase()

                        if (description == "LYRICS" || description == "SYNCEDLYRICS") {
                            val txxxLyrics = body.text
                            if (!txxxLyrics.isNullOrBlank()) {
                                Log.d(TAG, "Found TXXX lyrics with description '$description' in ${filePath.substringAfterLast('/')}")
                                return txxxLyrics
                            }
                        }
                    }
                }

                // SYLT FRAME CHECK: Binary Synchronized Lyrics
                val syltFrames = tag.getFields("SYLT")
                if (syltFrames.isNotEmpty()) {
                    val syltFrame = syltFrames[0] as AbstractID3v2Frame
                    val body = syltFrame.body as FrameBodySYLT

                    // Route the binary body into our custom parser!
                    val parsedLrc = parseSyltToLrc(body)
                    if (!parsedLrc.isNullOrBlank()) {
                        return parsedLrc
                    }
                }
            }

            // No lyrics found in any standard or fallback fields
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses a binary SYLT (Synchronized Lyric Text) frame body into a standard LRC string.
     */
    fun parseSyltToLrc(body: FrameBodySYLT): String? {
        return try {
            // ID3v2 text encoding: 0=ISO-8859-1, 1=UTF-16, 2=UTF-16BE, 3=UTF-8
            val encoding = body.textEncoding.toInt()
            // Timestamp format: 1=Absolute time (MPEG frames), 2=Absolute time (Milliseconds)
            val timeStampFormat = body.timeStampFormat

            // Note: Depending on your exact jaudiotagger version, if body.lyrics isn't directly
            // accessible, use: body.getObjectValue("Lyrics") as ByteArray
            val lyricsBytes = body.lyrics ?: return null

            val isMilliseconds = (timeStampFormat == 2)
            val isUtf16 = (encoding == 1 || encoding == 2)

            // SYLT timestamps are stored as 32-bit big-endian integers
            val buffer = ByteBuffer.wrap(lyricsBytes).order(ByteOrder.BIG_ENDIAN)
            val lrcBuilder = StringBuilder()

            while (buffer.hasRemaining()) {
                val textBytes = mutableListOf<Byte>()

                // -> Read the text until the null terminator is found
                while (buffer.hasRemaining()) {
                    val b = buffer.get()
                    textBytes.add(b)

                    if (isUtf16) {
                        // UTF-16 uses a 2-byte null terminator
                        if (textBytes.size >= 2 &&
                                textBytes[textBytes.size - 2] == 0.toByte() &&
                                textBytes[textBytes.size - 1] == 0.toByte()) {
                            break
                        }
                    } else {
                        // ISO-8859-1 and UTF-8 use a 1-byte null terminator
                        if (b == 0.toByte()) {
                            break
                        }
                    }
                }

                // -> Each text string is immediately followed by a 4-byte timestamp
                if (buffer.remaining() < 4) break
                val timestampInt = buffer.getInt()

                // -> Determine the correct charset for decoding the text
                val charset = when (encoding) {
                    0 -> Charsets.ISO_8859_1
                    1 -> Charsets.UTF_16
                    2 -> Charsets.UTF_16BE
                    3 -> Charsets.UTF_8
                    else -> Charsets.ISO_8859_1
                }

                // -> Strip the null terminator(s) before converting to a String
                val trimLength = if (isUtf16) 2 else 1
                val cleanBytes = textBytes.dropLast(trimLength).toByteArray()
                val textLine = String(cleanBytes, charset).trim()

                // -> Convert timestamp to standard [mm:ss.xx] LRC format
                // (If the format uses MPEG frames, we still treat it as ms here for safety,
                // as calculating true MPEG frames requires the audio's exact frame rate)
                val timeInMs = timestampInt.toLong()

                val minutes = timeInMs / 60000
                val seconds = (timeInMs % 60000) / 1000
                val centiseconds = (timeInMs % 1000) / 10

                // Using Lang.US prevents issues in regions where commas are used instead of decimals
                val timeTag = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centiseconds)
                lrcBuilder.append(timeTag).append(textLine).append("\n")
            }

            lrcBuilder.toString().trim().ifEmpty { null }

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}