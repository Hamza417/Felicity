package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogAudioStateSnapshotBinding
import app.simple.felicity.engine.managers.AudioPipelineManager
import app.simple.felicity.engine.model.AudioPipelineSnapshot
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.theme.managers.ThemeManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Bottom-sheet dialog that displays a real-time [AudioPipelineSnapshot] for the
 * currently playing audio track.
 *
 * The dialog observes [AudioPipelineManager.snapshotFlow] and re-binds its views
 * whenever the service pushes an updated snapshot — e.g., on track change, decoder
 * initialization, output-device change, or the 3-second periodic pulse.
 *
 * Each pipeline stage is presented in order:
 *  1. Track Info (format, bit depth, sample rate, bitrate, channels)
 *  2. Decoder (active codec name)
 *  3. Resampler (input rate, output rate, quality)
 *  4. DSP (PCM format, sample rate, EQ preset, stereo expand, buffers, latency)
 *  5. Output Device (device name, bit depth in/out, hardware sample rate)
 *
 * @author Hamza417
 */
class AudioPipelineDialog : ScopedBottomSheetFragment() {

    private var binding: DialogAudioStateSnapshotBinding? = null

    companion object {
        private const val TAG = "AudioPipelineDialog"

        /**
         * Creates a new instance of [AudioPipelineDialog] with no arguments.
         *
         * @return A ready-to-show [AudioPipelineDialog] instance.
         */
        fun newInstance(): AudioPipelineDialog = AudioPipelineDialog()

        /**
         * Convenience extension that shows the pipeline dialog from any [FragmentManager].
         */
        fun FragmentManager.showAudioPipeline() {
            if (findFragmentByTag(TAG) == null) {
                newInstance().show(this, TAG)
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = DialogAudioStateSnapshotBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Immediately show whatever the service last pushed so the dialog never
        // opens blank when a snapshot is already available.
        AudioPipelineManager.snapshotFlow.value?.let {
            bindSnapshot(it)
        }

        // Ask the service to push a fresh snapshot right now. This is needed when
        // the dialog opens between periodic pulses, or when the player is paused.
        AudioPipelineManager.requestRefresh()

        lifecycleScope.launch {
            AudioPipelineManager.snapshotFlow
                .filterNotNull()
                .collect { snapshot ->
                    Log.d("AudioPipelineDialog", "Received new snapshot: $snapshot")
                    bindSnapshot(snapshot)
                }
        }
    }

    /**
     * Populates every consolidated [app.simple.felicity.decorations.typeface.TypeFaceTextView]
     * in the layout with the data from [snapshot].
     *
     * Every field is rendered as a [Spanned] string produced by [createSpannedString]:
     * the label part uses the theme primary text color and the value part uses the
     * secondary text color. All labels are loaded from string resources; dynamic numeric
     * or compound values are formatted via string-resource format strings so the display
     * text never contains hard-coded English literals.
     *
     * @param snapshot The latest fully-populated pipeline snapshot from [AudioPipelineManager].
     */
    private fun bindSnapshot(snapshot: AudioPipelineSnapshot) {
        val b = binding ?: return

        // Track Info
        b.valueFormat.text = createSpannedString(
                getString(R.string.format),
                snapshot.trackFormat.ifBlank { "—" })

        b.valueBitDepth.text = createSpannedString(
                getString(R.string.bit_depth),
                if (snapshot.bitDepth > 0) getString(R.string.format_bit_depth, snapshot.bitDepth) else "—")

        b.valueSampleRate.text = createSpannedString(
                getString(R.string.sample_rate),
                if (snapshot.sampleRateHz > 0) getString(R.string.format_hz, snapshot.sampleRateHz) else "—")

        b.valueBitrate.text = createSpannedString(
                getString(R.string.bitrate),
                if (snapshot.bitrateKbps > 0) getString(R.string.format_kbps, snapshot.bitrateKbps) else "—")

        b.valueChannels.text = createSpannedString(
                getString(R.string.channels),
                when (snapshot.channels) {
                    0 -> "—"
                    1 -> getString(R.string.channel_mono)
                    2 -> getString(R.string.channel_stereo)
                    else -> "${snapshot.channels}"
                })

        // Decoder
        b.valueDecoderName.text = createSpannedString(
                getString(R.string.decoder_name),
                snapshot.decoderName.ifBlank { "—" })

        // Resampler — I/O rates are merged into one value using a format string
        val inRate = if (snapshot.inputSampleRate > 0) getString(R.string.format_hz, snapshot.inputSampleRate) else "—"
        val outRate = if (snapshot.outputSampleRate > 0) getString(R.string.format_hz, snapshot.outputSampleRate) else "—"
        b.valueResamplerRates.text = createSpannedString(
                getString(R.string.io_rate),
                getString(R.string.format_io_rate, inRate, outRate))

        b.valueResamplerQuality.text = createSpannedString(
                getString(R.string.quality),
                snapshot.resamplerQuality.ifBlank { "—" })

        // DSP
        b.valueDspFormat.text = createSpannedString(
                getString(R.string.pcm_format),
                snapshot.dspFormat.ifBlank { "—" })

        b.valueDspSampleRate.text = createSpannedString(
                getString(R.string.sample_rate),
                if (snapshot.dspSampleRate > 0) getString(R.string.format_hz, snapshot.dspSampleRate) else "—")

        b.valueEqPreset.text = createSpannedString(
                getString(R.string.eq_preset),
                snapshot.activeEqName ?: getString(R.string.disabled))

        b.valueStereoExpand.text = createSpannedString(
                getString(R.string.stereo_expand),
                getString(R.string.format_percent, snapshot.stereoExpandPercent))

        b.valueBuffers.text = createSpannedString(
                getString(R.string.buffers),
                snapshot.buffers.ifBlank { "—" })

        b.valueLatency.text = createSpannedString(
                getString(R.string.latency),
                getString(R.string.format_approx_ms, snapshot.latencyMs))

        // Output Device — bit depths are merged into one value using a format string
        b.valueDeviceName.text = createSpannedString(
                getString(R.string.device_name),
                snapshot.deviceName.ifBlank { "—" })

        b.valueDeviceBitDepth.text = createSpannedString(
                getString(R.string.bit_depth),
                getString(R.string.format_bit_depth_io, snapshot.deviceBitDepthIn, snapshot.deviceBitDepthOut))

        b.valueDeviceSampleRate.text = createSpannedString(
                getString(R.string.sample_rate),
                if (snapshot.deviceSampleRate > 0) getString(R.string.format_hz, snapshot.deviceSampleRate) else "—")
    }

    /**
     * Builds a two-tone [Spanned] string in the form `"label: value"`.
     *
     * The label (including the `": "` separator) is colored with the theme's primary
     * text color so it reads as a subtle heading. The value is colored with the
     * secondary text color to visually distinguish it from the label.
     *
     * Colors are read from [ThemeManager.theme] at call time so they always reflect
     * the currently active theme without requiring any caching.
     *
     * @param label The descriptor shown before the colon (e.g., `"Sample Rate"`).
     * @param value The dynamic value shown after the colon (e.g., `"44100 Hz"`).
     * @return A fully-spanned [Spanned] ready to assign to any [android.widget.TextView].
     */
    private fun createSpannedString(label: String, value: String): Spanned {
        val separator = ": "
        val full = "$label$separator$value"
        val spannable = SpannableString(full)
        val valueStart = label.length + separator.length

        val primaryColor = ThemeManager.theme.textViewTheme.primaryTextColor
        val secondaryColor = ThemeManager.theme.textViewTheme.secondaryTextColor

        spannable.setSpan(
                ForegroundColorSpan(primaryColor),
                0, valueStart,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.setSpan(
                ForegroundColorSpan(secondaryColor),
                valueStart, full.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
