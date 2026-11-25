package app.simple.felicity.repository.constants

import java.io.File

object FileConstants {

    /**
     * Map of audio extensions to their full names e.g. "mp3" to "MPEG-x Audio Layer III"
     */
    /**
     * Map of audio extensions to their full format names.
     * Includes standard, lossless, mobile-specific, cinematic, and Android ringtone formats.
     */
    private val AUDIO_EXTENSIONS_MAP = mapOf(
            // Common Lossy Formats
            "mp3" to "MPEG Audio Layer III",
            "aac" to "Advanced Audio Coding",
            "m4a" to "MPEG-4 Audio",
            "m4b" to "MPEG-4 Audiobook",
            "ogg" to "Ogg Vorbis",
            "opus" to "Opus Audio Codec",
            "wma" to "Windows Media Audio",
            // WMV is a video container, but it can hold audio-only streams too.
            // We only index WMV files that have no video track.
            "wmv" to "Windows Media Video (Audio Only)",
            "webma" to "WebM Audio",
            "webm" to "WebM Audio",

            // Lossless & Uncompressed Formats
            "wav" to "Waveform Audio File Format",
            "flac" to "Free Lossless Audio Codec",
            "alac" to "Apple Lossless Audio Codec",
            "pcm" to "Pulse-Code Modulation",
            "aiff" to "Audio Interchange File Format",
            "aif" to "Audio Interchange File Format",
            "ape" to "Monkey's Audio",

            // Mobile / Speech / Telephony Formats
            "amr" to "Adaptive Multi-Rate Audio Codec",
            "awb" to "Adaptive Multi-Rate Wideband",
            "3gp" to "3GPP Multimedia File",
            "3gpp" to "3GPP Multimedia File",
            "3ga" to "3GPP Audio",

            // Cinematic / Surround Formats
            "ac3" to "Dolby Digital (AC-3)",
            "ec3" to "Dolby Digital Plus (E-AC-3)",
            "dts" to "Digital Theater Systems",
            "mka" to "Matroska Audio",

            // MIDI & Legacy Android Ringtone Formats
            "mid" to "Musical Instrument Digital Interface",
            "midi" to "Musical Instrument Digital Interface",
            "xmf" to "Extensible Music Format",
            "mxmf" to "Mobile Extensible Music Format",
            "rtttl" to "Ring Tone Transfer Language",
            "rtx" to "Ring Tone Text Transfer Language",
            "ota" to "Over-The-Air Ringtone",
            "imy" to "iMelody Ringtone Format"
    )

    fun File.getAudioFormat(): String? {
        val extension = this.extension.lowercase()
        return AUDIO_EXTENSIONS_MAP[extension]
    }

    fun String.getAudioFormat(): String? {
        val extension = this.substringAfterLast('.', "").lowercase()
        return AUDIO_EXTENSIONS_MAP[extension]
    }

    /**
     * Fallback MIME types for containers Android's [android.webkit.MimeTypeMap] does not
     * know about — most notably `m4b`, which is nothing more than an MP4 container with
     * audiobook chapters, so `audio/mp4` is the right type for it.
     */
    private val AUDIO_MIME_TYPES = mapOf(
            "m4b" to "audio/mp4",
            "m4a" to "audio/mp4",
            "m4p" to "audio/mp4",
            "alac" to "audio/mp4",
            "opus" to "audio/opus",
            "ape" to "audio/x-ape",
            "dsf" to "audio/x-dsf",
            "dff" to "audio/x-dff"
    )

    /**
     * Returns the MIME type for an extension [android.webkit.MimeTypeMap] doesn't recognize,
     * or null when we have nothing better to offer.
     */
    fun String.getAudioMimeType(): String? {
        val extension = this.substringAfterLast('.', "").lowercase()
        return AUDIO_MIME_TYPES[extension]
    }

    fun File.getAudioMimeType(): String? = this.name.getAudioMimeType()

    /**
     * True for audiobook containers (`.m4b`). Audiobooks are hours long and are usually
     * packaged as a single chapter-less file, so they get treated a bit differently:
     * online lyric lookups are skipped for them — searching LrcLib for a book title
     * would happily return the lyrics of a song that happens to share the name.
     */
    fun File.isAudiobook(): Boolean = this.extension.equals("m4b", ignoreCase = true)

    fun String.isAudiobook(): Boolean = this.substringAfterLast('.', "").equals("m4b", ignoreCase = true)
}