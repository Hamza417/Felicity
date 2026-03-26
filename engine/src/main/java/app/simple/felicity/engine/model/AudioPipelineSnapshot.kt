package app.simple.felicity.engine.model

data class AudioPipelineSnapshot(
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
        val resamplerQuality: String,

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
)