package app.simple.felicity.ui.preferences.main

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentPurchaseBinding
import app.simple.felicity.databinding.HeaderPreferencesGenericBinding
import app.simple.felicity.extensions.fragments.PreferenceFragment
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.shared.utils.ViewUtils.gone
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

        refreshTrialStatus()
        observeBillingState()
        setupBuyButton()
        setupRestoreButton()
    }

    /**
     * Fills in the trial status text at the top of the screen.
     * If the user already purchased, we swap it out for a celebratory message instead.
     */
    private fun refreshTrialStatus() {
        binding.trialStatus.text = when {
            TrialPreferences.isFullVersion() -> getString(R.string.already_purchased)
            TrialPreferences.isWithinTrialPeriod() -> getString(R.string.trial_period_summary, TrialPreferences.getDaysLeft())
            else -> getString(R.string.trial_expired_short)
        }
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
                    hidePriceRow()
                }

                is PurchaseViewModel.BillingState.Ready -> {
                    if (state.offerPrice != null) {
                        // There is an active deal — show the struck-through original price
                        // alongside the shiny discounted offer price.
                        showPriceRow(basePrice = state.basePrice, offerPrice = state.offerPrice)
                        // The button itself shows the offer price so there is no confusion
                        // about what the user will actually be charged.
                        binding.buyButton.text = state.offerPrice
                    } else {
                        // No offer today — just show the normal price on the button.
                        hidePriceRow()
                        binding.buyButton.text = state.basePrice.ifEmpty { getString(R.string.purchase_now) }
                    }
                    binding.buyButton.isEnabled = true
                    binding.buyButton.alpha = 1f
                }

                is PurchaseViewModel.BillingState.AlreadyPurchased -> {
                    binding.buyButton.text = getString(R.string.already_purchased)
                    binding.buyButton.isEnabled = false
                    binding.buyButton.alpha = 0.6f
                    binding.restoreButton.isEnabled = false
                    binding.restoreButton.gone()
                    hidePriceRow()
                    refreshTrialStatus()
                }

                is PurchaseViewModel.BillingState.Error -> {
                    binding.buyButton.text = getString(R.string.billing_error, state.message)
                    binding.buyButton.isEnabled = true
                    binding.buyButton.alpha = 0.8f
                    hidePriceRow()
                }

                is PurchaseViewModel.BillingState.Unavailable -> {
                    binding.buyButton.text = getString(R.string.billing_unavailable)
                    binding.buyButton.isEnabled = false
                    binding.buyButton.alpha = 0.5f
                    hidePriceRow()
                }
            }
        }

        purchaseViewModel.purchaseSuccess.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                refreshTrialStatus()
            }
        }

        /**
         * When a manual restore attempt finds nothing, we show a friendly warning.
         * The message explains the "wrong account" scenario so the user knows exactly
         * what to do instead of staring blankly at the screen wondering what went wrong.
         */
        purchaseViewModel.restoreFailed.observe(viewLifecycleOwner) { failed ->
            if (failed == true) {
                showRestoreFailedWarning()
            }
        }
    }

    /**
     * Shows the price row with the original price crossed out and the offer price highlighted.
     * The strikethrough + grey combo makes it crystal clear that you're getting a deal.
     *
     * @param basePrice The regular price to display with a strikethrough.
     * @param offerPrice The discounted price shown in the accent-colored badge.
     */
    private fun showPriceRow(basePrice: String, offerPrice: String) {
        binding.originalPrice.text = basePrice
        // Apply a strikethrough so it looks properly "crossed out" — classic sale style.
        binding.originalPrice.paintFlags =
            binding.originalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        binding.offerPriceBadge.text = offerPrice
        binding.priceRow.visibility = View.VISIBLE
    }

    /**
     * Hides the entire price row — called when there is no active offer and
     * we just want to show a plain price on the button without any extra fuss.
     */
    private fun hidePriceRow() {
        binding.priceRow.visibility = View.GONE
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

    /**
     * Wires up the quieter "Restore Purchase" link below the main button.
     * This is for users who already paid but the app forgot — reinstalls,
     * new phones, or just the app having a bad memory day.
     *
     * The button dims itself while the restore is in progress so the user
     * knows something is happening and doesn't tap it seventeen times.
     */
    private fun setupRestoreButton() {
        binding.restoreButton.setOnClickListener {
            binding.restoreButton.isEnabled = false
            binding.restoreButton.text = getString(R.string.restore_purchase_restoring)
            purchaseViewModel.restorePurchase()
        }

        // Re-enable the button whenever the billing state settles, so the user
        // can try again if needed — no stuck buttons allowed.
        purchaseViewModel.billingState.observe(viewLifecycleOwner) {
            if (it !is PurchaseViewModel.BillingState.AlreadyPurchased) {
                binding.restoreButton.isEnabled = true
                binding.restoreButton.text = getString(R.string.restore_purchase)
            }
        }
    }

    /**
     * Shows an alert dialog when a restore attempt comes back empty-handed.
     *
     * We explicitly mention the "wrong account" scenario because that's by far
     * the most common reason someone's purchase doesn't show up — and without
     * that hint, they'd probably email support immediately (fair enough, honestly).
     */
    private fun showRestoreFailedWarning() {
        context?.let { ctx ->
            AlertDialog.Builder(ctx)
                .setTitle(R.string.restore_purchase)
                .setMessage(R.string.restore_purchase_failed)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

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
