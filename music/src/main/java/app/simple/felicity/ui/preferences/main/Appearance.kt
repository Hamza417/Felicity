package app.simple.felicity.ui.preferences.main

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference

class Appearance : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.appearance

    override val iconRes: Int
        get() = R.drawable.ic_water_drop

    override fun getPreferencesList(): List<Preference> {
        return createAppearancePanel()
    }

    companion object {
        fun newInstance(): Appearance {
            val args = Bundle()
            val fragment = Appearance()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Appearance"
    }
}