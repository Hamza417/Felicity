package app.simple.felicity.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.databinding.FragmentTrialExpiredBinding
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.ui.preferences.main.Purchase

/**
 * Full-screen fragment shown as the root destination whenever the free trial period
 * has elapsed. Unlike the former bottom-sheet dialog approach, this fragment replaces
 * the entire home panel so the user must consciously act before entering the app.
 *
 * Navigation behavior:
 *  - "I'll Purchase Later" (grace period still active): increments the grace launch
 *    counter and navigates to home without adding anything to the back stack.
 *  - "Close" (grace period exhausted): terminates the activity.
 *  - "Purchase Now": opens the [Purchase] screen, adding it to the back stack so the
 *    user can return here if they change their mind.
 *
 * This screen is intentionally not cancelable via the system back gesture when the
 * grace period is fully exhausted, because pressing back on the root fragment simply
 * exits the activity — the desired paywall behavior.
 *
 * @author Hamza417
 */
class TrialExpired : PanelFragment() {

    private lateinit var binding: FragmentTrialExpiredBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTrialExpiredBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        val gracePeriodExpired = TrialPreferences.isGracePeriodExpired()

        if (gracePeriodExpired) {
            binding.trialMessage.text = getString(R.string.grace_period_expired_message)
            binding.graceCounter.gone(animate = false)
            binding.closeOrLater.text = getString(R.string.close)
            binding.closeOrLater.setOnClickListener {
                requireActivity().finish()
            }
        } else {
            binding.trialMessage.text = getString(R.string.trial_expired_message)
            val used = TrialPreferences.getGraceLaunchesUsed()
            val max = TrialPreferences.MAX_GRACE_LAUNCHES
            binding.graceCounter.text = getString(R.string.grace_launches_used, used, max)
            binding.closeOrLater.text = getString(R.string.purchase_later)
            binding.closeOrLater.setOnClickListener {
                TrialPreferences.incrementGraceLaunches()
                (requireActivity() as MainActivity).showHome()
            }
        }

        binding.purchaseNow.setOnClickListener {
            openFragment(Purchase.newInstance(), Purchase.TAG)
        }
    }

    companion object {
        /**
         * Creates a new instance of [TrialExpired].
         *
         * @return A ready-to-show [TrialExpired] fragment instance.
         */
        fun newInstance(): TrialExpired {
            val args = Bundle()
            val fragment = TrialExpired()
            fragment.arguments = args
            return fragment
        }

        /** Back-stack tag used when navigating to this fragment. */
        const val TAG = "TrialExpired"
    }
}

