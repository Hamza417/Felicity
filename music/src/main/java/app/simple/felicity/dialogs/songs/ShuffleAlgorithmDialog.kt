package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogShuffleAlgorithmBinding
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

        when (ShufflePreferences.getShuffleAlgorithm()) {
            ShufflePreferences.ALGORITHM_FISHER_YATES -> {
                binding.fisherYates.isChecked = true
                binding.algorithmDescription.text = getString(R.string.fisher_yates_desc)
            }
            ShufflePreferences.ALGORITHM_MILLER -> {
                binding.miller.isChecked = true
                binding.algorithmDescription.text = getString(R.string.miller_desc)
            }
        }

        binding.algorithmChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.fisherYates.id -> {
                    ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_FISHER_YATES)
                    binding.algorithmDescription.text = getString(R.string.fisher_yates_desc)
                }
                binding.miller.id -> {
                    ShufflePreferences.setShuffleAlgorithm(ShufflePreferences.ALGORITHM_MILLER)
                    binding.algorithmDescription.text = getString(R.string.miller_desc)
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
