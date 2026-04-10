package app.simple.felicity.ui.preferences.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPreferenceAppearanceBinding
import app.simple.felicity.databinding.HeaderPreferencesGenericBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.GumroadLicenseKey.Companion.showGumroadLicenseKey
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.TrialPreferences

class Purchase : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceAppearanceBinding
    private lateinit var headerBinding: HeaderPreferencesGenericBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesGenericBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        headerBinding.title.text = getString(R.string.purchase)
        headerBinding.icon.setImageResource(R.drawable.ic_sell)
        binding.header.setContentView(headerBinding.root)
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = GenericPreferencesAdapter(createPurchasePanel())
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

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

        preferences.add(trialHeader)
        preferences.add(trialPeriod)
        preferences.add(gumroadHeader)
        preferences.add(purchaseHeader)
        preferences.add(purchase)

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