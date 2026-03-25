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
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.preferences.VisualizerPreferences
import java.util.Locale

class VisualizerConfig : MediaBottomDialogFragment() {

    private lateinit var binding: DialogVisualizerTuningBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogVisualizerTuningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateLyricsAlignmentState()

        binding.visualizerToggle.isChecked = PlayerPreferences.isVisualizerEnabled()
        binding.visualizerToggle.setOnCheckedChangeListener { _, isChecked ->
            PlayerPreferences.setVisualizerEnabled(isChecked)
        }

        binding.textSizeSeekbar.setProgress(VisualizerPreferences.getVisualizerLatency().toFloat())
        binding.textSizeSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    VisualizerPreferences.setVisualizerLatency(progress.toLong())
                }
            }
        })

        binding.textSizeSeekbar.setRightLabelProvider { progress, f1, f2 ->
            String.format(Locale.getDefault(), "%d ms", progress.toInt())
        }

        binding.particlesToggle.isChecked = VisualizerPreferences.areParticlesEnabled()
        binding.particlesToggle.setOnCheckedChangeListener { _, isChecked ->
            VisualizerPreferences.setParticlesEnabled(isChecked)
        }
    }

    fun updateLyricsAlignmentState() {
        binding.styleToggle.iconSize = 14F
        binding.styleToggle.setButtons(
                listOf(
                        Button(iconResId = R.drawable.ic_equalizer_16dp),
                        Button(iconResId = R.drawable.ic_waves),
                )
        )

        when (VisualizerPreferences.getVisualizerType()) {
            VisualizerPreferences.TYPE_BARS -> binding.styleToggle.setSelectedIndex(0)
            VisualizerPreferences.TYPE_WAVE -> binding.styleToggle.setSelectedIndex(1)
        }

        binding.styleToggle.setOnButtonSelectedListener { index ->
            when (index) {
                0 -> VisualizerPreferences.setVisualizerType(VisualizerPreferences.TYPE_BARS)
                1 -> VisualizerPreferences.setVisualizerType(VisualizerPreferences.TYPE_WAVE)
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
    }
}

