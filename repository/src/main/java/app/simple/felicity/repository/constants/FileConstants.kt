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
            "ogg" to "Ogg Vorbis",
            "opus" to "Opus Audio Codec",
            "wma" to "Windows Media Audio",

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
}