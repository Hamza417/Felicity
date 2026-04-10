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
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.TrialPreferences

/**
 * Purchase screen for the Play Store flavor of Felicity.
 *
 * Displays trial status information and a placeholder purchase flow pointing users to
 * the Play Store listing. Full in-app billing integration is reserved for a future release;
 * currently a web-link fallback is provided so users can proceed with a purchase immediately.
 *
 * @author Hamza417
 */
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

        val playStoreHeader = Preference(
                title = R.string.play_store,
                type = PreferenceType.SUB_HEADER
        )

        val playStorePurchase = Preference(
                title = R.string.play_store,
                summary = R.string.play_store_purchase_summary,
                icon = R.drawable.ic_sell,
                type = PreferenceType.LINK,
                onPreferenceAction = { _, _ ->
                    val intent = Intent(
                            Intent.ACTION_VIEW,
                            "https://play.google.com/store/apps/details?id=app.simple.felicity".toUri()
                    )
                    startActivity(intent)
                }
        )

        val featuresHeader = Preference(
                title = R.string.features,
                type = PreferenceType.SUB_HEADER
        )

        val featuresInfo = Preference(
                title = R.string.play_store_features,
                type = PreferenceType.WARN
        )

        preferences.add(trialHeader)
        preferences.add(trialPeriod)
        preferences.add(playStoreHeader)
        preferences.add(playStorePurchase)
        preferences.add(featuresHeader)
        preferences.add(featuresInfo)

        return preferences
    }

    companion object {
        /**
         * Creates and returns a new instance of [Purchase].
         *
         * @return A new [Purchase] fragment instance with empty arguments.
         */
        fun newInstance(): Purchase {
            val args = Bundle()
            val fragment = Purchase()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Purchase"
    }
}

