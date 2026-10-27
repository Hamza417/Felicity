package app.simple.felicity.ui.preferences.main

import android.content.SharedPreferences
import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.UserInterfacePreferences

class Configuration : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.configration

    override val iconRes: Int
        get() = R.drawable.ic_app_settings

    override fun getPreferencesList(): List<Preference> {
        return createConfigurationPreferences()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            UserInterfacePreferences.HOME_INTERFACE -> {

            }
            UserInterfacePreferences.PLAYER_INTERFACE -> {

            }
        }
    }

    companion object {
        fun newInstance(): Configuration {
            val args = Bundle()
            val fragment = Configuration()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Configuration"
    }
}