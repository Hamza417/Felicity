package app.simple.felicity.engine.utils

import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.models.Audio

/**
 * Utility object for formatting PCM audio stream properties of a playing track
 * into a compact, human-readable info string.
 *
 * Output order: bit depth → sample rate (kHz) → bitrate (kbps) → file format extension.
 * Fields that carry a zero or null value are silently omitted from the result so the
 * string degrades gracefully for lossy formats that lack bit-depth metadata.
 *
 * @author Hamza417
 */
object PcmInfoFormatter {

    /**
     * Builds a compact PCM info string for the given [audio] track.
     *
     * Properties are appended in the following order, separated by single spaces:
     *  1. Bit depth     — e.g. `16bit`, `24bit`, `32bit` (omitted when 0)
     *  2. Sample rate   — e.g. `44.1kHz`, `48kHz`, `96kHz` (omitted when 0)
     *  3. Bitrate       — e.g. `320 kbps`, `967 kbps` (omitted when 0)
     *  4. Format        — uppercase file extension, e.g. `FLAC`, `MP3`, `OPUS` (omitted when path is null)
     *
     * @param audio The [Audio] track whose metadata is used.
     * @return A formatted string such as `"24bit 96kHz 3500 kbps FLAC"`,
     *         or an empty string if no metadata is available.
     */
    fun formatPcmInfo(audio: Audio): String {
        return buildString {
            if (PlayerPreferences.getPcmInfoMode() == PlayerPreferences.PCM_INFO_MODE_SAMPLING_RATE) {
                if (audio.bitPerSample > 0) {
                    append("${audio.bitPerSample}bit")
                }

                if (audio.samplingRate > 0) {
                    if (isNotEmpty()) append(" ")
                    append(audio.samplingRate.toKhzString())
                }
            }

            if (PlayerPreferences.getPcmInfoMode() == PlayerPreferences.PCM_INFO_MODE_BITRATE) {
                if (audio.bitrate > 0) {
                    if (isNotEmpty()) append(" ")
                    append("${audio.bitrate} kbit/s")
                }

                val ext = audio.path?.substringAfterLast('.', "")?.uppercase()
                if (!ext.isNullOrEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(ext)
                }
            }

            if (PlayerPreferences.getPcmInfoMode() == PlayerPreferences.PCM_INFO_MODE_QUALITY) {
                when (audio.audioQuality) {
                    Audio.AUDIO_QUALITY_LQ -> {
                        append("LQ")
                    }
                    Audio.AUDIO_QUALITY_HQ -> {
                        append("HQ")
                    }
                    Audio.AUDIO_QUALITY_LOSSLESS -> {
                        append("Lossless")
                    }
                    Audio.AUDIO_QUALITY_HI_RES -> {
                        append("Hi-Res")
                    }
                    else -> {

                    }
                }
            }
        }
    }

    /**
     * Converts a sample rate value expressed in Hz to a human-readable kHz string.
     *
     * Whole-number results are displayed without a decimal point (e.g. `48000` → `"48kHz"`),
     * while fractional results retain the minimum number of digits needed
     * (e.g. `44100` → `"44.1kHz"`).
     *
     * @return A formatted kHz string such as `"44.1kHz"` or `"96kHz"`.
     */
    private fun Long.toKhzString(): String {
        val kHz = this / 1000.0
        return if (kHz % 1.0 == 0.0) "${kHz.toInt()}kHz" else "${kHz}kHz"
    }
}

