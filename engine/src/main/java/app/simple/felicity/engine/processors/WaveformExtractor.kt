package app.simple.felicity.engine.processors

/**
 * JNI wrapper for the miniaudio-based waveform amplitude extraction engine.
 *
 * Delegates to a native C++ function that opens the audio file once, reads its
 * entire PCM stream linearly via miniaudio's built-in decoders (dr_mp3, dr_flac,
 * dr_wav), and computes the peak absolute amplitude for every bar window in a
 * single sequential pass.  The result is globally normalized to [0.0, 1.0].
 *
 * This approach has no per-bar seek or codec-flush overhead, making it orders of
 * magnitude faster than a seek-per-bar MediaCodec strategy for long tracks.
 *
 * Supported formats: MP3, FLAC, WAV.
 * Unsupported formats (e.g., AAC/M4A): [extractWaveform] returns null; callers
 * should substitute a ghost/placeholder waveform in that case.
 *
 * The underlying native shared library is the same [felicity_audio_engine] shared
 * object used by [VisualizerProcessor] — [System.loadLibrary] is safe to call
 * multiple times for the same library (subsequent calls are no-ops).
 *
 * @author Hamza417
 */
class WaveformExtractor {

    /**
     * Extracts normalized amplitude data from the audio file at [path].
     *
     * Opens the file with miniaudio, requests mono float32 output at the file's
     * native sample rate, then accumulates the peak amplitude over each window of
     * [sampleRate / barsPerSecond] frames.  After all frames are read the array is
     * globally normalized so the loudest bar equals 1.0.
     *
     * The call is synchronous and blocks the calling thread until decoding is
     * complete. Run it on a background dispatcher (e.g., [kotlinx.coroutines.Dispatchers.IO]).
     *
     * @param path         Absolute filesystem path to the audio file.
     * @param durationMs   Total track duration in milliseconds; used to pre-size the output array.
     * @param barsPerSecond Number of amplitude bars per second of audio (default 1).
     * @return Normalized [FloatArray] with values in [0.0, 1.0], or null if miniaudio
     *         could not open or decode the file (e.g., unsupported format).
     */
    fun extractWaveform(
            path: String,
            durationMs: Long,
            barsPerSecond: Int = 1
    ): FloatArray? {
        return nativeExtractWaveform(path, durationMs, barsPerSecond)
    }

    /**
     * Native entry point.  Implemented in [waveform-extractor.cpp].
     *
     * @param path         Absolute path to the audio file.
     * @param durationMs   Track duration in milliseconds.
     * @param barsPerSecond Bars-per-second density for the output array.
     * @return Normalized amplitude [FloatArray], or null on failure.
     */
    private external fun nativeExtractWaveform(
            path: String,
            durationMs: Long,
            barsPerSecond: Int
    ): FloatArray?

    companion object {

        init {
            /**
             * The native implementation lives in the same shared library as
             * [VisualizerProcessor].  Repeated [System.loadLibrary] calls for the
             * same library name are silently ignored by the runtime.
             */
            System.loadLibrary("felicity_audio_engine")
        }
    }
}

