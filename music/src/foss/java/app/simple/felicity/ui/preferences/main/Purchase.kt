package app.simple.felicity.ui.preferences.main

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import app.simple.felicity.R
import app.simple.felicity.dialogs.app.GumroadLicenseKey.Companion.showGumroadLicenseKey
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.TrialPreferences

class Purchase : PreferenceFragment() {

    override val titleRes: Int
        get() = R.string.purchase

    override val iconRes: Int
        get() = R.drawable.ic_sell

    override fun getPreferencesList(): List<Preference> {
        return createPurchasePanel()
    }

    private fun createPurchasePanel(): MutableList<Preference> {
        val preferences = mutableListOf<Preference>()

        val trialHeader = Preference(
                title = R.string.trial,
                type = PreferenceType.SUB_HEADER
        )

        val trialIcon = if (TrialPreferences.isWithinTrialPeriod()) {
            R.drawable.ic_hourglass_top
        } else {
            R.drawable.ic_hourglass_bottom
        }

        val trialPeriod = Preference(
                title = R.string.trial_period,
                summary = getString(R.string.trial_period_summary, TrialPreferences.getDaysLeft()),
                icon = trialIcon,
                type = PreferenceType.NORMAL
        )

        val gumroadHeader = Preference(
                title = R.string.gumroad,
                type = PreferenceType.SUB_HEADER
        )

        val purchaseHeader = Preference(
                title = R.string.gumroad,
                summary = R.string.support_development_summary,
                icon = R.drawable.ic_gumroad,
                type = PreferenceType.LINK,
                onPreferenceAction = { view, callback ->
                    val intent = Intent(Intent.ACTION_VIEW, "https://hamza417.gumroad.com/l/Felicity/".toUri())
                    startActivity(intent)
                }
        )

        val purchase = Preference(
                title = R.string.enter_license_key,
                summary = R.string.enter_license_key_summary,
                icon = R.drawable.ic_key,
                type = PreferenceType.DIALOG,
                onPreferenceAction = { view, callback ->
                    childFragmentManager.showGumroadLicenseKey()
                }
        )

        val playstoreHeader = Preference(
                title = R.string.play_store,
                type = PreferenceType.SUB_HEADER
        )

        val playStoreInfo = Preference(
                title = R.string.play_store_foss_purchase_info,
                type = PreferenceType.WARN
        )

        val playStoreLink = Preference(
                title = R.string.playstore_link,
                summary = R.string.playstore_link_summary,
                icon = R.drawable.ic_play_store,
                type = PreferenceType.LINK,
                onPreferenceAction = { view, callback ->
                    val intent = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=app.simple.felicity".toUri())
                    startActivity(intent)
                }
        )

        preferences.add(trialHeader)
        preferences.add(trialPeriod)
        preferences.add(gumroadHeader)
        preferences.add(purchaseHeader)
        preferences.add(purchase)
        preferences.add(playstoreHeader)
        preferences.add(playStoreInfo)
        preferences.add(playStoreLink)

        return preferences
    }

    companion object {
        fun newInstance(): Purchase {
            val args = Bundle()
            val fragment = Purchase()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Purchase"
    }
}