package app.simple.felicity.ui.home

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterArtFlowHome
import app.simple.felicity.databinding.FragmentHomeArtflowBinding
import app.simple.felicity.decorations.flowsidemenu.FelicitySideBar
import app.simple.felicity.decorations.utils.RecyclerViewUtils.forEachViewHolder
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.pages.GenrePage
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.ArtFlow
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.main.home.HomeViewModel
import kotlinx.coroutines.FlowPreview

class ArtFlowHome : MediaFragment() {

    private lateinit var binding: FragmentHomeArtflowBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentHomeArtflowBinding.inflate(inflater, container, false)

        return binding.root
    }

    @OptIn(FlowPreview::class)
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        binding.recyclerView.setBackgroundColor(Color.BLACK)
        binding.recyclerView.requireAttachedMiniPlayer()

        binding.sideBar.attachToRecyclerView(binding.recyclerView)
        binding.sideBar.setCenterItemsVertically(true)
        binding.sideBar.setItemStyle(backgroundColor = ThemeManager.theme.viewGroupTheme.backgroundColor)
        binding.sideBar.setItems(listOf(
                FelicitySideBar.SidebarItem(R.drawable.ic_song),
                FelicitySideBar.SidebarItem(R.drawable.ic_artist),
                FelicitySideBar.SidebarItem(R.drawable.ic_album),
                FelicitySideBar.SidebarItem(R.drawable.ic_volume),
                FelicitySideBar.SidebarItem(R.drawable.ic_play)
        ))

        binding.sideBar.setOnItemClickListener { id, view ->
            Log.d(TAG, "Sidebar item clicked with id: $id")
            when (id) {
                R.drawable.ic_song -> {
                    openFragment(Songs.newInstance(), Songs.TAG)
                }
                R.drawable.ic_artist -> {
                    openFragment(Artists.newInstance(), Artists.TAG)
                }
                R.drawable.ic_album -> {
                    openFragment(Albums.newInstance(), Albums.TAG)
                }
                R.drawable.ic_volume -> {
                    childFragmentManager.showVolumeKnob()
                }
                R.drawable.ic_play -> {
                    openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
                }
                else -> {
                    Log.w(TAG, "Unknown sidebar item clicked with id: $id")
                }
            }
        }

        homeViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "Data received: ${data.size} items")
            val adapter = AdapterArtFlowHome(data)

            binding.recyclerView.adapter = adapter
            binding.recyclerView.setHasFixedSize(true)

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
                            openFragment(ArtFlow.newInstance(), ArtFlow.TAG)
                        }
                        R.string.artists -> {
                            openFragment(Artists.newInstance(), Artists.TAG)
                        }
                        R.string.albums -> {
                            openFragment(Albums.newInstance(), Albums.TAG)
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
                    childFragmentManager.showHomeMenu()
                }
            })

            binding.recyclerView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        binding.recyclerView.forEachViewHolder<AdapterArtFlowHome.Holder> {
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
