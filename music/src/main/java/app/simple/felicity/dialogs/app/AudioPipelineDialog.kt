package app.simple.felicity.dialogs.app

import android.os.Bundle
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
        Log.d("AudioPipelineDialog", "Requesting snapshot refresh on dialog open")
        AudioPipelineManager.requestRefresh()
        Log.d("AudioPipelineDialog", "Requested snapshot refresh on dialog open")

        // Collect every future update for as long as the fragment is alive.
        // Using lifecycleScope (tied to the fragment lifecycle, not the view lifecycle)
        // means the coroutine starts immediately in onViewCreated and is not gated
        // behind the view lifecycle reaching STARTED — which can be delayed inside a
        // BottomSheetDialogFragment, causing the first emission to be missed.
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
     * Populates every value [TypeFaceTextView] in the layout with the data from [snapshot].
     *
     * All string formatting (unit suffixes, channel labels, null-safety) is handled here
     * so the layout XML stays clean.
     *
     * @param snapshot The latest fully-populated pipeline snapshot from [AudioPipelineManager].
     */
    private fun bindSnapshot(snapshot: AudioPipelineSnapshot) {
        Log.d("AudioPipelineDialog", "Binding snapshot: $snapshot")

        val b = binding ?: return

        // Track Info
        b.valueFormat.text = snapshot.trackFormat
        b.valueBitDepth.text = if (snapshot.bitDepth > 0) "${snapshot.bitDepth}-bit" else "—"
        b.valueSampleRate.text = if (snapshot.sampleRateHz > 0) "${snapshot.sampleRateHz} Hz" else "—"
        b.valueBitrate.text = if (snapshot.bitrateKbps > 0) "${snapshot.bitrateKbps} kbps" else "—"
        b.valueChannels.text = when (snapshot.channels) {
            0 -> "—"
            1 -> "1 (Mono)"
            2 -> "2 (Stereo)"
            else -> "${snapshot.channels}"
        }

        // Decoder
        b.valueDecoderName.text = snapshot.decoderName

        // Resampler
        b.valueInputSampleRate.text = if (snapshot.inputSampleRate > 0) "${snapshot.inputSampleRate} Hz" else "—"
        b.valueOutputSampleRate.text = if (snapshot.outputSampleRate > 0) "${snapshot.outputSampleRate} Hz" else "—"
        b.valueResamplerQuality.text = snapshot.resamplerQuality

        // DSP
        b.valueDspFormat.text = snapshot.dspFormat
        b.valueDspSampleRate.text = if (snapshot.dspSampleRate > 0) "${snapshot.dspSampleRate} Hz" else "—"
        b.valueEqPreset.text = snapshot.activeEqName ?: getString(R.string.disabled)
        b.valueStereoExpand.text = "${snapshot.stereoExpandPercent}%"
        b.valueBuffers.text = snapshot.buffers
        b.valueLatency.text = "~${snapshot.latencyMs} ms"

        // Output Device
        b.valueDeviceName.text = snapshot.deviceName
        b.valueDeviceBitDepthIn.text = "${snapshot.deviceBitDepthIn}-bit"
        b.valueDeviceBitDepthOut.text = "${snapshot.deviceBitDepthOut}-bit"
        b.valueDeviceSampleRate.text = if (snapshot.deviceSampleRate > 0) "${snapshot.deviceSampleRate} Hz" else "—"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
