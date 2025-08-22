package app.simple.felicity.dialogs.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogHomeMenuBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.HomePreferences

class HomeMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogHomeMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogHomeMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setHomeInterfaceType()

        binding.changeHomeInterface.setOnClickListener { it ->

        }

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    private fun setHomeInterfaceType() {
        when (HomePreferences.getHomeInterface()) {
            HomePreferences.HOME_INTERFACE_SPANNED -> {
                binding.changeHomeInterface.setText(R.string.spanned)
            }
            HomePreferences.HOME_INTERFACE_CAROUSEL -> {
                binding.changeHomeInterface.setText(R.string.carousel)
            }
            HomePreferences.HOME_INTERFACE_ARTFLOW -> {
                binding.changeHomeInterface.setText(R.string.artflow)
            }
            HomePreferences.HOME_INTERFACE_SIMPLE -> {
                binding.changeHomeInterface.setText(R.string.simple)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            HomePreferences.HOME_INTERFACE -> {
                setHomeInterfaceType()
            }
        }
    }

    companion object {
        fun newInstance(): HomeMenu {
            val args = Bundle()
            val fragment = HomeMenu()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "HomeMenu"

        fun FragmentManager.showHomeMenu(): HomeMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}