package app.simple.felicity.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardPanels
import app.simple.felicity.adapters.home.dashboard.AdapterDashboardSongs
import app.simple.felicity.databinding.FragmentHomeDahsboardBinding
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Favorites
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.panels.RecentlyAdded
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.viewmodels.panels.DashboardViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Dashboard home screen fragment.
 *
 * Displays a scrollable dashboard composed of:
 * - A header with the app name, search, and settings buttons.
 * - A horizontal carousel of recently played songs (substituted with recently added
 *   songs until a dedicated history database is available).
 * - A four-column browse grid of seven panel navigation shortcuts plus an expand item
 *   that opens the full simple home panel list.
 * - A horizontal carousel of recently added songs.
 * - A horizontal carousel of favorite songs.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Dashboard : MediaFragment() {

    private lateinit var binding: FragmentHomeDahsboardBinding

    private val dashboardViewModel: DashboardViewModel by viewModels()

    private var recentlyPlayedAdapter: AdapterDashboardSongs? = null
    private var recentlyAddedAdapter: AdapterDashboardSongs? = null
    private var favoritesAdapter: AdapterDashboardSongs? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeDahsboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showMiniPlayer()
        setupHeader()
        setupPanelsGrid()
        setupCarouselSpacing()
        observeData()
    }

    private fun setupHeader() {
        binding.search.setOnClickListener {
            // openSearch()
        }
        binding.menu.setOnClickListener {
            childFragmentManager.showHomeMenu()
        }
    }

    private fun setupPanelsGrid() {
        val adapter = AdapterDashboardPanels(dashboardViewModel.panelItems)
        binding.panelsRecyclerView.layoutManager = GridLayoutManager(requireContext(), PANEL_SPAN_COUNT)
        binding.panelsRecyclerView.adapter = adapter
        adapter.setCallbacks(object : AdapterDashboardPanels.Companion.AdapterDashboardPanelsCallbacks {
            override fun onPanelClicked(element: Element) {
                navigateToPanel(element)
            }

            override fun onExpandClicked() {
                openFragment(SimpleHome.newInstance(), SimpleHome.TAG)
            }
        })
    }

    private fun setupCarouselSpacing() {
        val spacing = resources.getDimensionPixelSize(R.dimen.carousel_spacing)
        binding.recentlyPlayedList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
        binding.recentlyAddedList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
        binding.favoritesList.addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dashboardViewModel.recentlyPlayed.collect { songs ->
                        updateRecentlyPlayed(songs)
                    }
                }
                launch {
                    dashboardViewModel.recentlyAdded.collect { songs ->
                        updateRecentlyAdded(songs)
                    }
                }
                launch {
                    dashboardViewModel.favorites.collect { songs ->
                        updateFavorites(songs)
                    }
                }
            }
        }
    }

    private fun updateRecentlyPlayed(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.recentlyPlayedSection.gone()
            return
        }
        binding.recentlyPlayedSection.visible(false)
        if (recentlyPlayedAdapter == null) {
            recentlyPlayedAdapter = AdapterDashboardSongs(songs)
            recentlyPlayedAdapter!!.setCallbacks(object : AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.recentlyPlayedList.adapter = recentlyPlayedAdapter
        } else {
            recentlyPlayedAdapter!!.updateData(songs)
        }
    }

    private fun updateRecentlyAdded(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.recentlyAddedSection.gone()
            return
        }
        binding.recentlyAddedSection.visible(false)
        if (recentlyAddedAdapter == null) {
            recentlyAddedAdapter = AdapterDashboardSongs(songs)
            recentlyAddedAdapter!!.setCallbacks(object : AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.recentlyAddedList.adapter = recentlyAddedAdapter
        } else {
            recentlyAddedAdapter!!.updateData(songs)
        }
    }

    private fun updateFavorites(songs: List<Audio>) {
        if (songs.isEmpty()) {
            binding.favoritesSection.gone()
            return
        }
        binding.favoritesSection.visible(false)
        if (favoritesAdapter == null) {
            favoritesAdapter = AdapterDashboardSongs(songs)
            favoritesAdapter!!.setCallbacks(object : AdapterDashboardSongs.Companion.AdapterDashboardSongsCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int) {
                    setMediaItems(songs, position)
                }
            })
            binding.favoritesList.adapter = favoritesAdapter
        } else {
            favoritesAdapter!!.updateData(songs)
        }
    }

    private fun navigateToPanel(element: Element) {
        when (element.titleResId) {
            R.string.songs -> openFragment(Songs.newInstance(), Songs.TAG)
            R.string.albums -> openFragment(Albums.newInstance(), Albums.TAG)
            R.string.artists -> openFragment(Artists.newInstance(), Artists.TAG)
            R.string.genres -> openFragment(Genres.newInstance(), Genres.TAG)
            R.string.favorites -> openFragment(Favorites.newInstance(), Favorites.TAG)
            R.string.playing_queue -> openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
            R.string.recently_added -> openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
        }
    }

    companion object {
        /**
         * Creates a new instance of [Dashboard].
         *
         * @return A fresh [Dashboard] fragment.
         */
        fun newInstance(): Dashboard = Dashboard()

        /** Back-stack tag used when navigating to this fragment. */
        const val TAG = "Dashboard"

        private const val PANEL_SPAN_COUNT = 4
    }
}