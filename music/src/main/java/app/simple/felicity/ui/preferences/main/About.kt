package app.simple.felicity.ui.preferences.main

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference

class About : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.about

    override val iconRes: Int
        get() = R.drawable.ic_info

    override fun getPreferencesList(): List<Preference> {
        return createAboutPanel()
    }

    companion object {
        fun newInstance(): About {
            val args = Bundle()
            val fragment = About()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "About"
    }
}