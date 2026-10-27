package app.simple.felicity.ui.preferences.main

import android.os.Bundle
import app.simple.felicity.R
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference

class Behavior : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.behavior

    override val iconRes: Int
        get() = R.drawable.ic_behavior

    override fun getPreferencesList(): List<Preference> {
        return createBehaviorPanel()
    }

    companion object {
        fun newInstance(): Behavior {
            val args = Bundle()
            val fragment = Behavior()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Behavior"
    }
}