package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogVolumeKnobBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment

class VolumeKnob : ScopedBottomSheetFragment() {

    private var binding: DialogVolumeKnobBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogVolumeKnobBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        fun newInstance(): VolumeKnob {
            val args = Bundle()
            val fragment = VolumeKnob()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showVolumeKnob(): VolumeKnob {
            if (!isVolumeKnobShowing()) {
                val dialog = newInstance()
                dialog.show(this, TAG)
                return dialog
            }

            return findFragmentByTag(TAG) as VolumeKnob
        }

        fun AppCompatActivity.showVolumeKnob(): VolumeKnob {
            if (!supportFragmentManager.isVolumeKnobShowing()) {
                val dialog = newInstance()
                dialog.show(supportFragmentManager, TAG)
                return dialog
            }

            return supportFragmentManager.findFragmentByTag(TAG) as VolumeKnob
        }

        private fun FragmentManager.isVolumeKnobShowing(): Boolean {
            return findFragmentByTag(TAG) != null
        }

        const val TAG = "VolumeKnob"
    }
}
