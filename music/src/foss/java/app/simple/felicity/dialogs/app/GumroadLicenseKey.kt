package app.simple.felicity.dialogs.app

import android.animation.LayoutTransition
import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogLicenseKeyBinding
import app.simple.felicity.decorations.utils.TextViewUtils.doOnTextChanged
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.viewmodels.GumroadLicenceAuthenticatorViewModel

class GumroadLicenseKey : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogLicenseKeyBinding
    private lateinit var inputFilter: InputFilter
    private val pattern = "0123456789ABCDEF-" // The pattern for the licence key

    private val gumroadLicenceAuthenticatorViewModel: GumroadLicenceAuthenticatorViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogLicenseKeyBinding.inflate(inflater, container, false)

        binding.verify.gone()
        binding.info.gone()

        val transition = LayoutTransition()
        transition.setAnimateParentHierarchy(false)
        (binding.dialogPurchase).layoutTransition = transition
        (binding.licenseKeyContainer).layoutTransition = transition

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gumroadLicenceAuthenticatorViewModel.getLicenseStatus().observe(viewLifecycleOwner) {
            if (it) {
                Log.d(TAG, "Licence is valid")
                handler.post {
                    dismiss()
                }
            } else {
                Log.d(TAG, "Licence is invalid")
            }
        }

        inputFilter = InputFilter { source, _, _, _, _, _ ->
            val string = source.toString()
            for (element in string) {
                if (!pattern.contains(element)) {
                    return@InputFilter ""
                }
            }

            source
        }

        binding.licenseKey.doOnTextChanged { text, _, _, _ ->
            if (text.toString().length == 35) {
                binding.verify.visible(false)
            } else {
                binding.verify.gone()
            }
        }

        binding.licenseKey.filters = arrayOf(inputFilter)

        binding.verify.setOnClickListener {
            gumroadLicenceAuthenticatorViewModel.verifyLicence(binding.licenseKey.text.toString())
            binding.info.setText(R.string.verifying_license)
            binding.info.visible(animate = true)
        }

        gumroadLicenceAuthenticatorViewModel.getMessage().observe(viewLifecycleOwner) {
            binding.info.text = it
            binding.info.visible(animate = true)
        }

        gumroadLicenceAuthenticatorViewModel.getWarning()?.observe(viewLifecycleOwner) {
            binding.info.text = it
            binding.info.visible(animate = true)
        }
    }

    companion object {
        fun newInstance(): GumroadLicenseKey {
            val args = Bundle()
            val fragment = GumroadLicenseKey()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showGumroadLicenseKey() {
            val dialog = newInstance()
            dialog.show(this, TAG)
        }

        const val TAG = "GumroadLicenseKey"
    }
}