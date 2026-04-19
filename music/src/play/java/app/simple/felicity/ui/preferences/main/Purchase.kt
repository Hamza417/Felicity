package app.simple.felicity.ui.preferences.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPurchaseBinding
import app.simple.felicity.databinding.HeaderPreferencesGenericBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.enums.PreferenceType
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.models.Preference
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.viewmodels.PurchaseViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Purchase screen for the Play Store flavor of Felicity.
 *
 * This screen connects to the Google Play Billing service to show the user the real
 * price of the app (pulled live from the Play Store, so no outdated numbers), lets
 * them complete the purchase, and marks the app as fully unlocked when they do.
 *
 * The giant button at the bottom is impossible to miss — we're proud of it.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Purchase : PreferenceFragment() {

    private var _binding: FragmentPurchaseBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: HeaderPreferencesGenericBinding

    /**
     * The ViewModel handles all the heavy billing work so this fragment can stay
     * clean and focused on just showing things on screen.
     */
    private val purchaseViewModel: PurchaseViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPurchaseBinding.inflate(inflater, container, false)
        headerBinding = HeaderPreferencesGenericBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        headerBinding.title.text = getString(R.string.purchase)
        headerBinding.icon.setImageResource(R.drawable.ic_sell)
        binding.header.setContentView(headerBinding.root)
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.adapter = GenericPreferencesAdapter(buildInfoPanel())
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        observeBillingState()
        setupBuyButton()
    }

    /**
     * Watches the billing state from the ViewModel and updates the UI to match.
     * Each state gets its own button label and enabled/disabled state so the user
     * always knows what's happening — no mysterious spinning wheels.
     */
    private fun observeBillingState() {
        purchaseViewModel.billingState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PurchaseViewModel.BillingState.Connecting -> {
                    binding.buyButton.text = getString(R.string.billing_connecting)
                    binding.buyButton.isEnabled = false
                    binding.buyButton.alpha = 0.6f
                }

                is PurchaseViewModel.BillingState.Ready -> {
                    // Show the real price straight from the Play Store — no guessing.
                    val price = state.productDetails
                        .oneTimePurchaseOfferDetails
                        ?.formattedPrice
                        ?: getString(R.string.purchase_now)
                    binding.buyButton.text = price
                    binding.buyButton.isEnabled = true
                    binding.buyButton.alpha = 1f
                }

                is PurchaseViewModel.BillingState.AlreadyPurchased -> {
                    binding.buyButton.text = getString(R.string.already_purchased)
                    binding.buyButton.isEnabled = false
                    binding.buyButton.alpha = 0.6f
                    // Refresh the info panel to reflect the updated full-version status.
                    binding.recyclerView.adapter = GenericPreferencesAdapter(buildInfoPanel())
                }

                is PurchaseViewModel.BillingState.Error -> {
                    binding.buyButton.text = getString(R.string.billing_error, state.message)
                    binding.buyButton.isEnabled = true
                    binding.buyButton.alpha = 0.8f
                }

                is PurchaseViewModel.BillingState.Unavailable -> {
                    binding.buyButton.text = getString(R.string.billing_unavailable)
                    binding.buyButton.isEnabled = false
                    binding.buyButton.alpha = 0.5f
                }
            }
        }

        purchaseViewModel.purchaseSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                // Let the user know they're now a proud owner of the full version!
                binding.recyclerView.adapter = GenericPreferencesAdapter(buildInfoPanel())
            }
        }
    }

    /**
     * Wires up the big buy button. Tapping it asks the ViewModel to open the
     * Google Play purchase sheet — the ViewModel takes it from there.
     */
    private fun setupBuyButton() {
        binding.buyButton.setOnClickListener {
            activity?.let { act ->
                purchaseViewModel.launchPurchaseFlow(act)
            }
        }
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    /**
     * Builds the list of info cards shown at the top of the screen.
     * These show the trial status so the user understands why they're here
     * and how many days they have left before things get awkward.
     */
    private fun buildInfoPanel(): MutableList<Preference> {
        val preferences = mutableListOf<Preference>()

        preferences.add(Preference(title = R.string.trial, type = PreferenceType.SUB_HEADER))

        if (TrialPreferences.isFullVersion()) {
            preferences.add(
                    Preference(
                            title = R.string.full_version_active,
                            summary = getString(R.string.already_purchased),
                            icon = R.drawable.ic_sell,
                            type = PreferenceType.NORMAL
                    )
            )
        } else {
            val trialIcon = if (TrialPreferences.isWithinTrialPeriod()) {
                R.drawable.ic_hourglass_top
            } else {
                R.drawable.ic_hourglass_bottom
            }

            preferences.add(
                    Preference(
                            title = R.string.trial_period,
                            summary = if (TrialPreferences.isWithinTrialPeriod()) {
                                getString(R.string.trial_period_summary, TrialPreferences.getDaysLeft())
                            } else {
                                getString(R.string.trial_expired_short)
                            },
                            icon = trialIcon,
                            type = PreferenceType.NORMAL
                    )
            )
        }

        preferences.add(Preference(title = R.string.purchase, type = PreferenceType.SUB_HEADER))
        preferences.add(
                Preference(
                        title = R.string.buy_full_version,
                        summary = R.string.play_store_purchase_summary,
                        icon = R.drawable.ic_sell,
                        type = PreferenceType.NORMAL
                )
        )

        return preferences
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
