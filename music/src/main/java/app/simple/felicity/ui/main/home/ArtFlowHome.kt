package app.simple.felicity.ui.main.home

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.home.main.AdapterArtFlowHome
import app.simple.felicity.core.R
import app.simple.felicity.databinding.FragmentHomeArtflowBinding
import app.simple.felicity.decorations.utils.RecyclerViewUtils.forEachViewHolder
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.app.CoverFlow
import app.simple.felicity.ui.main.albums.PeristyleAlbums
import app.simple.felicity.ui.main.artists.PeristyleArtists
import app.simple.felicity.ui.main.genres.GenrePage
import app.simple.felicity.ui.main.genres.Genres
import app.simple.felicity.viewmodels.main.home.HomeViewModel
import kotlinx.coroutines.FlowPreview

class ArtFlowHome : MediaFragment() {

    private var binding: FragmentHomeArtflowBinding? = null
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentHomeArtflowBinding.inflate(inflater, container, false)

        return binding?.root
    }

    @OptIn(FlowPreview::class)
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        binding?.recyclerView?.setBackgroundColor(Color.BLACK)

        homeViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "Data received: ${data.size} items")
            val adapter = AdapterArtFlowHome(data)

            binding?.recyclerView?.adapter = adapter
            binding?.recyclerView?.setHasFixedSize(true)
            binding?.recyclerView?.backgroundTintList =
                ColorStateList(arrayOf(intArrayOf()), intArrayOf(Color.BLACK))

            adapter.setAdapterArtFlowHomeCallbacks(object : AdapterArtFlowHome.Companion.AdapterArtFlowHomeCallbacks {
                override fun onClicked(view: View, position: Int, itemPosition: Int) {
                    Log.d(TAG, "Item clicked at position: $position")
                    when (data[position].items[0]) {
                        is Song -> {
                            setMediaItems(data[position].items.filterIsInstance<Song>(), itemPosition)
                        }
                        is Genre -> {
                            val genre = data[position].items.filterIsInstance<Genre>()[itemPosition]
                            openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                        }
                        else -> {
                            Log.w(TAG, "Unsupported item type clicked at position: $position")
                        }
                    }
                }

                override fun onClicked(view: View, position: Int) {

                }

                override fun onPanelItemClicked(title: Int, view: View) {
                    Log.d(TAG, "Panel item clicked with title: $title")
                    when (title) {
                        R.string.songs -> {
                            openFragment(CoverFlow.newInstance(), CoverFlow.TAG)
                        }
                        R.string.artists -> {
                            openFragment(PeristyleArtists.newInstance(), PeristyleArtists.TAG)
                        }
                        R.string.albums -> {
                            openFragment(PeristyleAlbums.newInstance(), PeristyleAlbums.TAG)
                        }
                        R.string.genres -> {
                            openFragment(Genres.newInstance(), GenrePage.TAG)
                        }
                        else -> {
                            Log.w(TAG, "Unknown panel item clicked with title: $title")
                        }
                    }
                }

                override fun onMenuClicked(view: View) {
                    parentFragmentManager.showHomeMenu()
                }
            })

            binding?.recyclerView?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        binding?.recyclerView?.forEachViewHolder<AdapterArtFlowHome.Holder> {
                            postDelayed(1_000L) {
                                it.binding.imageSlider.restartCycle()
                            }
                        }
                    }
                    MotionEvent.ACTION_DOWN -> {

                    }
                }

                false
            }

            requireView().startTransitionOnPreDraw()
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

        const val TAG = "ArtFlowHome"
        private const val DELAY = 5_000L
    }
}
