package app.simple.felicity.ui.main.home

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.home.main.AdapterGridHome
import app.simple.felicity.adapters.home.sub.AdapterGridArt
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentHomeSpannedBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.randomViewHolder
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.home.HomeViewModel

class SpannedHome : ScopedFragment() {

    private lateinit var binding: FragmentHomeSpannedBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeSpannedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        binding.recyclerView.setBackgroundColor(Color.BLACK)

        homeViewModel.getData().observe(viewLifecycleOwner) {
            val adapter = AdapterGridHome(it)
            binding.recyclerView.setHasFixedSize(false)
            binding.recyclerView.adapter = adapter
            binding.recyclerView.scheduleLayoutAnimation()
            binding.recyclerView.itemAnimator = null

            adapter.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    parentFragmentManager.showHomeMenu()
                }
            })
        }
    }

    private val randomizer: Runnable = object : Runnable {
        override fun run() {
            try {
                binding.recyclerView.randomViewHolder<AdapterGridHome.Holder> { holder ->
                    holder.binding.artGrid.animate()!!
                        .alpha(0F)
                        .setDuration(resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                        .withEndAction {
                            (holder.binding.artGrid.adapter as AdapterGridArt).randomize()
                            holder.binding.artGrid.scheduleLayoutAnimation()
                            holder.binding.artGrid.animate()!!
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

        const val TAG = "SpannedHome"
        private const val DELAY = 3_000L
        private const val BASIC_DURATION = 1_500L
        private const val SMALL_DURATION = 250L
    }
}
