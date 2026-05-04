package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogShuffleAlgorithmBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.ShufflePreferences

class ShuffleDialog : ScopedBottomSheetFragment() {

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
    }

    companion object {
        private const val TAG = "ShuffleDialog"

        fun newInstance(): ShuffleDialog {
            val fragment = ShuffleDialog()
            fragment.arguments = Bundle()
            return fragment
        }

        fun FragmentManager.showShuffleDialog(): ShuffleDialog {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}
