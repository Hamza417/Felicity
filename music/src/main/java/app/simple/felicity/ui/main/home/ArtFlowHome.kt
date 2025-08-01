package app.simple.felicity.ui.main.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.adapters.home.main.AdapterArtFlowHome
import app.simple.felicity.databinding.FragmentHomeArtflowBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.forEachViewHolder
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.ui.main.songs.InureSongs
import app.simple.felicity.viewmodels.main.home.HomeViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch

class ArtFlowHome : ScopedFragment() {

    private var binding: FragmentHomeArtflowBinding? = null
    private var homeViewModel: HomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentHomeArtflowBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        return binding?.root
    }

    @OptIn(FlowPreview::class)
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()
        val adapter = AdapterArtFlowHome()
        binding?.recyclerView?.adapter = adapter
        binding?.recyclerView?.setHasFixedSize(true)
        binding?.recyclerView?.backgroundTintList = ColorStateList(arrayOf(intArrayOf()), intArrayOf(Color.BLACK))

        adapter.setAdapterArtFlowHomeCallbacks(object : AdapterArtFlowHome.Companion.AdapterArtFlowHomeCallbacks {
            override fun onClicked(view: View, position: Int) {
                openFragmentSlide(InureSongs.newInstance(), InureSongs.TAG)
            }
        })

        binding?.recyclerView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    binding?.recyclerView?.forEachViewHolder<AdapterArtFlowHome.Holder> {
                        it.sliderView.restartCycle()
                    }
                }
            }

            false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

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
