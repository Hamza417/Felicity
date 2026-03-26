package app.simple.felicity.engine.model

data class AudioPipelineSnapshot(
        // Track Info
        val trackFormat: String, // e.g., "FLAC"
        val bitDepth: Int,       // e.g., 16, 24
        val sampleRateHz: Int,   // e.g., 44100
        val bitrateKbps: Int,
        val channels: Int,

        // Decoder Info
        val decoderName: String, // e.g., "c2.android.flac.decoder", ffmpeg, etc.

        // Resampler State (From Audio Engine)
        val inputSampleRate: Int,
        val outputSampleRate: Int,
        /** Who is resampling: `"None"`, `"Software"`, `"Hardware (HAL)"`, or `"SW + HW"`. */
        val resamplerType: String,
        /** Resampler implementation quality: `"Passthrough"`, `"Android SRC"`, `"HAL Native"`, or `"Android SRC + HAL"`. */
        val resamplerQuality: String,
        /** Theoretical Nyquist anti-aliasing cutoff in Hz; `0` when resampling is bypassed. */
        val resamplerCutoffHz: Int,

        // DSP State
        val dspFormat: String,   // e.g., "Float32"
        val dspSampleRate: Int,
        val activeEqName: String?,
        val stereoExpandPercent: Int,
        val buffers: String, // e.g., "e.g. 2x (40ms, 1800 audio frames)"
        val latencyMs: Int, // Total latency from source to output in milliseconds

        // Hardware Output Device
        val deviceName: String,         // e.g., "realme Buds T310"
        val deviceBitDepthIn: Int,      // e.g., 16
        val deviceBitDepthOut: Int,     // e.g., 24
        val deviceSampleRate: Int
) {
    override fun toString(): String {
        return "AudioPipelineSnapshot(trackFormat='$trackFormat'," +
                " bitDepth=$bitDepth," +
                " sampleRateHz=$sampleRateHz," +
                " bitrateKbps=$bitrateKbps," +
                " channels=$channels," +
                " decoderName='$decoderName'," +
                " inputSampleRate=$inputSampleRate," +
                " outputSampleRate=$outputSampleRate," +
                " resamplerType='$resamplerType'," +
                " resamplerQuality='$resamplerQuality'," +
                " resamplerCutoffHz=$resamplerCutoffHz," +
                " dspFormat='$dspFormat'," +
                " dspSampleRate=$dspSampleRate," +
                " activeEqName=$activeEqName," +
                " stereoExpandPercent=$stereoExpandPercent," +
                " buffers='$buffers'," +
                " latencyMs=$latencyMs," +
                " deviceName='$deviceName'," +
                " deviceBitDepthIn=$deviceBitDepthIn," +
                " deviceBitDepthOut=$deviceBitDepthOut," +
                " deviceSampleRate=$deviceSampleRate)"
    }
}