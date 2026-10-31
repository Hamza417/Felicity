package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogVisualizerTuningBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.VisualizerPreferences
import java.util.Locale
import kotlin.math.roundToInt

class VisualizerConfig : MediaBottomDialogFragment() {

    private lateinit var binding: DialogVisualizerTuningBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogVisualizerTuningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateVizStyleState()

        binding.visualizerToggle.isChecked = PlayerPreferences.isVisualizerEnabled()
        binding.visualizerToggle.setOnCheckedChangeListener { _, isChecked ->
            PlayerPreferences.setVisualizerEnabled(isChecked)
        }

        setupLatencySeekbar()
    }

    private fun setupLatencySeekbar() {
        val seekbar = binding.latencySeekbar
        seekbar.setRange(
                VisualizerPreferences.LATENCY_OFFSET_MIN_MS.toFloat(),
                VisualizerPreferences.LATENCY_OFFSET_MAX_MS.toFloat()
        )
        seekbar.setDefaultProgress(0f)
        seekbar.setProgress(VisualizerPreferences.getLatencyOffsetMs().toFloat())
        seekbar.setRightLabelProvider { progress, _, _ ->
            String.format(Locale.getDefault(), "%+d ms", snapLatency(progress))
        }

        seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            private var isTracking = false

            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser && !isTracking) {
                    VisualizerPreferences.setLatencyOffsetMs(snapLatency(progress))
                }
            }

            override fun onStartTrackingTouch(seekbar: FelicitySeekbar) {
                isTracking = true
            }

            override fun onStopTrackingTouch(seekbar: FelicitySeekbar) {
                isTracking = false
                VisualizerPreferences.setLatencyOffsetMs(snapLatency(seekbar.getProgress()))
            }
        })
    }

    private fun snapLatency(progress: Float): Int {
        return (progress / LATENCY_STEP_MS).roundToInt() * LATENCY_STEP_MS
    }

    fun updateVizStyleState() {
        binding.styleToggle.iconSize = 14F
        binding.styleToggle.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_equalizer_16dp),
                        Button(iconResId = R.drawable.ic_waves),
                        Button(iconResId = R.drawable.ic_blocks_16dp),
                )
        )

        when (VisualizerPreferences.getVisualizerType()) {
            VisualizerPreferences.TYPE_BARS -> binding.styleToggle.setSelectedIndex(0)
            VisualizerPreferences.TYPE_WAVE -> binding.styleToggle.setSelectedIndex(1)
            VisualizerPreferences.TYPE_BLOCKS -> binding.styleToggle.setSelectedIndex(2)
        }

        binding.styleToggle.setOnButtonSelectedListener { index ->
            when (index) {
                0 -> VisualizerPreferences.setVisualizerType(VisualizerPreferences.TYPE_BARS)
                1 -> VisualizerPreferences.setVisualizerType(VisualizerPreferences.TYPE_WAVE)
                2 -> VisualizerPreferences.setVisualizerType(VisualizerPreferences.TYPE_BLOCKS)
            }
        }

        binding.openMilkdropVisualizer.setOnClickListener {
            (parentFragment as MediaFragment).openMilkdropPanel().also {
                // May mess with the predictive back if the dialog is left alive in the back stack
                // so we dismiss the dialog immediately after opening the settings.
                dismiss()
            }
        }
    }

    companion object {
        fun newInstance(): VisualizerConfig {
            val args = Bundle()
            val fragment = VisualizerConfig()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showVisualizerConfig(): VisualizerConfig {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "VisualizerConfig"
        private const val LATENCY_STEP_MS = 5
    }
}

