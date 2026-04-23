package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogWarningBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.shared.constants.BundleConstants

class Warning : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogWarningBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogWarningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val warning: Any = requireArguments().getString(BundleConstants.WARNING)
            ?: requireArguments().getInt(BundleConstants.WARNING)

        if (warning is String) {
            binding.warning.text = warning
        } else if (warning is Int) {
            binding.warning.setText(warning)
        }
    }

    companion object {
        fun newInstance(warning: String): Warning {
            val args = Bundle()
            args.putString(BundleConstants.WARNING, warning)
            val fragment = Warning()
            fragment.arguments = args
            return fragment
        }

        fun newInstance(@StringRes warning: Int): Warning {
            val args = Bundle()
            args.putInt(BundleConstants.WARNING, warning)
            val fragment = Warning()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showWarning(warning: String): Warning {
            val dialog = newInstance(warning)
            dialog.show(this, TAG)
            return dialog
        }

        fun FragmentManager.showWarning(@StringRes warning: Int): Warning {
            val dialog = newInstance(warning)
            dialog.show(this, TAG)
            return dialog
        }

        const val TAG = "AppLabel"
    }
}