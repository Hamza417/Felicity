package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogShuffleAlgorithmBinding
import app.simple.felicity.decorations.toggles.FelicityButtonGroup.Companion.Button
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.ShufflePreferences

class ShuffleAlgorithmDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogShuffleAlgorithmBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogShuffleAlgorithmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.shuffleSwitch.isChecked = ShufflePreferences.isShuffleEnabled()

        binding.shuffleSwitch.setOnCheckedChangeListener { _, isChecked ->
            ShufflePreferences.setShuffleEnabled(isChecked)
        }

        updateLyricsAlignmentState()
    }

    fun updateLyricsAlignmentState() {
        binding.shuffleAlgorithmGroup.setButtons(
                listOf(
                        Button(textResId = R.string.miller),
                        Button(textResId = R.string.fisher_yates)
                )
        )

        when (ShufflePreferences.getShuffleAlgorithm()) {
            ShufflePreferences.ALGORITHM_MILLER -> binding.shuffleAlgorithmGroup.setSelectedIndex(0)
            ShufflePreferences.ALGORITHM_FISHER_YATES -> binding.shuffleAlgorithmGroup.setSelectedIndex(1)
        }

        binding.shuffleAlgorithmGroup.setOnButtonSelectedListener {
            when (it) {
                0 -> {
                    ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_MILLER)
                }
                1 -> {
                    ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_FISHER_YATES)
                }
            }
        }
    }

    companion object {
        private const val TAG = "ShuffleAlgorithmDialog"

        fun newInstance(): ShuffleAlgorithmDialog {
            val args = Bundle()
            val fragment = ShuffleAlgorithmDialog()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showShuffleAlgorithmDialog(): ShuffleAlgorithmDialog {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
