package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogMainAppLabelBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.MainPreferences

class AppLabel : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogMainAppLabelBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogMainAppLabelBinding.inflate(inflater, container, false)



        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editText.setText(MainPreferences.getAppLabel(requireContext()))

        binding.save.setOnClickListener {
            val label = binding.editText.text.toString()
            MainPreferences.setAppLabel(label)
            dismiss()
        }
    }

    companion object {
        fun newInstance(): AppLabel {
            val args = Bundle()
            val fragment = AppLabel()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showAppLabel(): AppLabel {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        const val TAG = "AppLabel"
    }
}