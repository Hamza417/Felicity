package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogVisualizerTuningBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopupNonContainer
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.VisualizerPreferences
import java.util.Locale
import kotlin.math.roundToInt

class VisualizerMenu : MediaBottomDialogFragment() {

    private lateinit var binding: DialogVisualizerTuningBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogVisualizerTuningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateVizStyleState()

        binding.visualizerToggle.setChecked(PlayerPreferences.isVisualizerEnabled(), false)
        binding.visualizerToggle.setOnCheckedChangeListener { _, isChecked ->
            PlayerPreferences.setVisualizerEnabled(isChecked)
        }

        setupLatencySeekbar()

        binding.spectrumColorsToggle.setChecked(VisualizerPreferences.isSpectrumColorsEnabled(), false)
        binding.spectrumColorsToggle.setOnCheckedChangeListener { _, isChecked ->
            VisualizerPreferences.setSpectrumColorsEnabled(isChecked)
        }
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
        binding.stylePopup.setText(styleTitle(VisualizerPreferences.getVisualizerType()))

        binding.stylePopup.setOnClickListener { anchor ->
            SharedScrollViewPopupNonContainer(
                    container = binding.root,
                    anchorView = anchor,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.bars, icon = R.drawable.ic_equalizer_16dp),
                            PopupMenuItem(title = R.string.wave, icon = R.drawable.ic_waves),
                            PopupMenuItem(title = R.string.blocks, icon = R.drawable.ic_blocks_16dp)
                    ),
                    onMenuItemClick = {
                        val type = when (it) {
                            R.string.wave -> VisualizerPreferences.TYPE_WAVE
                            R.string.blocks -> VisualizerPreferences.TYPE_BLOCKS
                            else -> VisualizerPreferences.TYPE_BARS
                        }

                        VisualizerPreferences.setVisualizerType(type)
                        binding.stylePopup.setText(styleTitle(type))
                    }
            ).show()
        }

        binding.openMilkdropVisualizer.setOnClickListener {
            (parentFragment as MediaFragment).openMilkdropPanel().also {
                // May mess with the predictive back if the dialog is left alive in the back stack
                // so we dismiss the dialog immediately after opening the settings.
                dismiss()
            }
        }
    }

    private fun styleTitle(type: Int): Int {
        return when (type) {
            VisualizerPreferences.TYPE_WAVE -> R.string.wave
            VisualizerPreferences.TYPE_BLOCKS -> R.string.blocks
            else -> R.string.bars
        }
    }

    companion object {
        fun newInstance(): VisualizerMenu {
            val args = Bundle()
            val fragment = VisualizerMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showVisualizerMenu(): VisualizerMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "VisualizerMenu"
        private const val LATENCY_STEP_MS = 5
    }
}

