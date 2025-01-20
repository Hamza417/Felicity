package app.simple.felicity.ui.main.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.adapters.home.main.AdapterGridHome
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.randomViewHolder
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.home.HomeViewModel

class SpannedHome : ScopedFragment() {

    private var binding: FragmentHomeSpannedBinding? = null
    private var homeViewModel: HomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)

        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()
        binding?.recyclerView?.setHasFixedSize(false)

        homeViewModel?.getHomeData()?.observe(viewLifecycleOwner) {
            binding?.recyclerView?.adapter = AdapterGridHome(it)
            binding?.recyclerView?.scheduleLayoutAnimation()
            // Disable item animations
            binding?.recyclerView?.itemAnimator = null
        }
    }

    private val randomizer: Runnable = object : Runnable {
        override fun run() {
            try {
                binding?.recyclerView?.randomViewHolder<AdapterGridHome.Holder> { holder ->
                    holder.adapterGridHomeBinding?.artGrid?.animate()!!
                        .alpha(0F)
                        .setDuration(resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                        .withEndAction {
                            (holder.adapterGridHomeBinding?.artGrid?.adapter as AdapterGridArt).randomize()
                            holder.adapterGridHomeBinding?.artGrid?.scheduleLayoutAnimation()
                            holder.adapterGridHomeBinding?.artGrid?.animate()!!
                                .alpha(1F)
                                .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                                .start()
                        }
                        .start()
                }
            } catch (e: NoSuchElementException) {
                Log.e(TAG, "run: No such element", e)
            } catch (e: Exception) {
                Log.e(TAG, "run: Exception", e)
            }

            handler.postDelayed(this, DELAY)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(randomizer)
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(randomizer) // Just to be sure
        handler.postDelayed(randomizer, DELAY)
    }

    companion object {
        fun newInstance(): SpannedHome {
            val args = Bundle()
            val fragment = SpannedHome()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "SpannedHome"
        private const val DELAY = 3_000L
        private const val BASIC_DURATION = 1_500L
        private const val SMALL_DURATION = 250L
    }
}
