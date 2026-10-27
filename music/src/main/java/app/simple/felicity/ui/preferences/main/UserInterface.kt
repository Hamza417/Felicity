package app.simple.felicity.ui.preferences.main

import android.content.SharedPreferences
import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.UserInterfacePreferences

class UserInterface : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.user_interface

    override val iconRes: Int
        get() = R.drawable.ic_carousel

    override fun getPreferencesList(): List<Preference> {
        return createUserInterfacePanel()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            UserInterfacePreferences.HOME_INTERFACE -> {
                // Nothing extra to do here — the home switch handles reloading on its own.
            }
            UserInterfacePreferences.PLAYER_INTERFACE -> {
                // Nothing extra to do here either — the player fragment picks up the change.
            }
        }
    }

    companion object {
        fun newInstance(): UserInterface {
            val args = Bundle()
            val fragment = UserInterface()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "UserInterface"
    }
}