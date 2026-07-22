package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogWaveformMenuBinding
import app.simple.felicity.extensions.dialogs.MediaBottomDialogFragment
import app.simple.felicity.preferences.UserInterfacePreferences

class WaveformMenu : MediaBottomDialogFragment() {

    private lateinit var binding: DialogWaveformMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogWaveformMenuBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.stackMediaControlsSwitch.isChecked = UserInterfacePreferences.isStackMediaControls()

        binding.stackMediaControlsSwitch.setOnCheckedChangeListener { _, isChecked ->
            UserInterfacePreferences.setStackMediaControls(isChecked)
        }
    }

    companion object {
        fun newInstance(): WaveformMenu {
            val args = Bundle()
            val fragment = WaveformMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showWaveformMenu() {
            val dialog = newInstance()
            dialog.show(this, TAG)
        }

        const val TAG = "WaveformMenu"
    }
}