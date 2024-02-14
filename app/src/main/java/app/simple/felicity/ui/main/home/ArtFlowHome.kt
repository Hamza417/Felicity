package app.simple.felicity.ui.main.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterArtFlowHome
import app.simple.felicity.databinding.FragmentHomeArtflowBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.ui.app.Songs
import app.simple.felicity.utils.RecyclerViewUtils.forEachViewHolder
import app.simple.felicity.viewmodels.ui.HomeViewModel

class ArtFlowHome : ScopedFragment() {

    private var binding: FragmentHomeArtflowBinding? = null
    private var homeViewModel: HomeViewModel? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeArtflowBinding.inflate(inflater, container, false)

        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        return binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()
        binding?.recyclerView?.setHasFixedSize(true)

        homeViewModel?.getHomeData()?.observe(viewLifecycleOwner) { list ->
            binding?.recyclerView?.backgroundTintList = ColorStateList(
                    arrayOf(intArrayOf()),
                    intArrayOf(Color.BLACK)
            )

            binding?.recyclerView?.adapter = AdapterArtFlowHome(list)

            (binding?.recyclerView?.adapter as AdapterArtFlowHome).onContainerClicked = { _, position ->
                when (list[position].first) {
                    R.string.songs -> {
                        openFragmentSlide(Songs.newInstance(), "songs")
                    }
                }
            }

            binding?.recyclerView?.scheduleLayoutAnimation()

            binding?.recyclerView?.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        binding?.recyclerView?.forEachViewHolder<AdapterArtFlowHome.Holder> {
                            it.sliderView.restartCycle()
                        }
                    }
                }
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        fun newInstance(): ArtFlowHome {
            val args = Bundle()
            val fragment = ArtFlowHome()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "ArtFlowHome"
        private const val DELAY = 5_000L
    }
}