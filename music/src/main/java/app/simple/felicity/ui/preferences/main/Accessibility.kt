package app.simple.felicity.ui.preferences.main

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.AccessibilityPreferences

class Accessibility : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.accessibility

    override val iconRes: Int
        get() = R.drawable.ic_accessibility

    override fun getPreferencesList(): List<Preference> {
        return createAccessibilityPanel()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AccessibilityPreferences.STROKE_AROUND_MINIPLAYER -> {
                Log.d(TAG, "onSharedPreferenceChanged: stroke around miniplayer changed")
                peekMiniPlayer()
            }
            AccessibilityPreferences.DARKER_MINIPLAYER_SHADOW -> {
                Log.d(TAG, "onSharedPreferenceChanged: darker miniplayer shadow changed")
                peekMiniPlayer()
            }
        }
    }

    companion object {
        fun newInstance(): Accessibility {
            val args = Bundle()
            val fragment = Accessibility()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Accessibility"
    }
}