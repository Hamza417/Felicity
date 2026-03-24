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
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Favorites
import app.simple.felicity.ui.panels.Folders
import app.simple.felicity.ui.panels.FoldersHierarchy
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.MostPlayed
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.Preferences
import app.simple.felicity.ui.panels.RecentlyAdded
import app.simple.felicity.ui.panels.RecentlyPlayed
import app.simple.felicity.ui.panels.Search
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.ui.panels.Year
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.panels.HomeViewModel
import kotlinx.coroutines.FlowPreview

/**
 * Home fragment that presents curated song collections (Favorites, Recently Played, Most Played,
 * and Recently Added) as full-bleed image sliders. The sidebar provides quick access to the three
 * main browsing screens and a popup menu exposing every secondary panel available in the app.
 *
 * @author Hamza417
 */
class ArtFlowHome : MediaFragment() {

    private lateinit var binding: FragmentHomeArtflowBinding
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeArtflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(FlowPreview::class)
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireLightBarIcons()
        requireTransparentMiniPlayer()
        binding.recyclerView.setBackgroundColor(Color.BLACK)
        binding.recyclerView.requireAttachedMiniPlayer()

        binding.sideBar.attachToRecyclerView(binding.recyclerView)
        binding.sideBar.setCenterItemsVertically(true)
        binding.sideBar.setItemStyle(backgroundColor = ThemeManager.theme.viewGroupTheme.backgroundColor)
        binding.sideBar.setItems(listOf(
                FelicitySideBar.SidebarItem(R.drawable.ic_song),
                FelicitySideBar.SidebarItem(R.drawable.ic_artist),
                FelicitySideBar.SidebarItem(R.drawable.ic_album),
                FelicitySideBar.SidebarItem(R.drawable.ic_menu),
                FelicitySideBar.SidebarItem(R.drawable.ic_play),
                FelicitySideBar.SidebarItem(R.drawable.ic_search),
                FelicitySideBar.SidebarItem(R.drawable.ic_settings)
        ))

        binding.sideBar.setOnItemClickListener { id, anchorView ->
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
                R.drawable.ic_menu -> {
                    SharedScrollViewPopup(
                            container = requireActivity().findViewById(R.id.app_container),
                            anchorView = anchorView,
                            menuItems = listOf(
                                    R.string.genres,
                                    R.string.year,
                                    R.string.folders,
                                    R.string.folders_hierarchy,
                                    R.string.playing_queue,
                                    R.string.favorites,
                                    R.string.recently_added,
                                    R.string.recently_played,
                                    R.string.most_played
                            ),
                            menuIcons = listOf(
                                    R.drawable.ic_piano,
                                    R.drawable.ic_date_range,
                                    R.drawable.ic_folder,
                                    R.drawable.ic_tree,
                                    R.drawable.ic_queue,
                                    R.drawable.ic_favorite_filled,
                                    R.drawable.ic_recently_added,
                                    R.drawable.ic_history,
                                    R.drawable.ic_equalizer
                            ),
                            onMenuItemClick = { itemResId ->
                                when (itemResId) {
                                    R.string.genres -> openFragment(Genres.newInstance(), Genres.TAG)
                                    R.string.year -> openFragment(Year.newInstance(), Year.TAG)
                                    R.string.folders -> openFragment(Folders.newInstance(), Folders.TAG)
                                    R.string.folders_hierarchy -> openFragment(FoldersHierarchy.newInstance(), FoldersHierarchy.TAG)
                                    R.string.playing_queue -> openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                                    R.string.favorites -> openFragment(Favorites.newInstance(), Favorites.TAG)
                                    R.string.recently_added -> openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
                                    R.string.recently_played -> openFragment(RecentlyPlayed.newInstance(), RecentlyPlayed.TAG)
                                    R.string.most_played -> openFragment(MostPlayed.newInstance(), MostPlayed.TAG)
                                    else -> Log.w(TAG, "Unknown popup item clicked: $itemResId")
                                }
                            },
                            onDismiss = {}
                    ).show()
                }
                R.drawable.ic_play -> {
                    openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
                }
                R.drawable.ic_search -> {
                    openFragment(Search.newInstance(), Search.TAG)
                }
                R.drawable.ic_settings -> {
                    openFragment(Preferences.newInstance(), Preferences.TAG)
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
                    Log.d(TAG, "Item clicked at position: $position, itemPosition: $itemPosition")
                    when (data[position].items[0]) {
                        is Audio -> {
                            val audio = data[position].items.filterIsInstance<Audio>()[itemPosition]
                            Log.d(TAG, "Audio item clicked: ${audio.title}")
                        }
                        else -> {
                            Log.w(TAG, "Unsupported item type clicked at position: $position")
                        }
                    }
                }

                override fun onClicked(view: View, position: Int) {
                    Log.d(TAG, "Section container clicked at position: $position")
                }

                override fun onPanelItemClicked(title: Int, view: View) {
                    Log.d(TAG, "Panel item clicked with title: $title")
                    when (title) {
                        R.string.favorites -> {
                            openFragment(Favorites.newInstance(), Favorites.TAG)
                        }
                        R.string.recently_played -> {
                            openFragment(RecentlyPlayed.newInstance(), RecentlyPlayed.TAG)
                        }
                        R.string.most_played -> {
                            openFragment(MostPlayed.newInstance(), MostPlayed.TAG)
                        }
                        R.string.recently_added -> {
                            openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
                        }
                        else -> {
                            Log.w(TAG, "Unknown panel item clicked with title: $title")
                        }
                    }
                }
            })

            binding.recyclerView.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        binding.recyclerView.forEachViewHolder<AdapterArtFlowHome.Holder> {
                            postDelayed(1_000L) {
                                it.binding.felicitySlider.start()
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
        /**
         * Creates a new instance of [ArtFlowHome].
         *
         * @return A freshly instantiated [ArtFlowHome] fragment.
         */
        fun newInstance(): ArtFlowHome {
            val args = Bundle()
            val fragment = ArtFlowHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "ArtFlowHome"
    }
}




