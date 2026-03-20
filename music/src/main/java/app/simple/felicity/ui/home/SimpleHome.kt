package app.simple.felicity.ui.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.databinding.HeaderHomeBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.home.SimpleHomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.HomePreferences
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
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.ui.panels.Year
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Panel

class SimpleHome : PanelFragment() {

    private lateinit var binding: FragmentHomeSimpleBinding
    private lateinit var headerBinding: HeaderHomeBinding

    private var homeViewModel: SimpleHomeViewModel? = null
    private var adapterSimpleHome: AdapterSimpleHome? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)
        headerBinding = HeaderHomeBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        gridLayoutManager = GridLayoutManager(requireContext(), 1)
        updateGridLayoutSpanCount()
        binding.recyclerView.layoutManager = gridLayoutManager

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showHomeMenu()
        }

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            adapterSimpleHome = AdapterSimpleHome(list)
            adapterSimpleHome!!.setLayoutType(HomePreferences.getHomeLayoutType())
            setupAdapterCallbacks()
            binding.recyclerView.adapter = adapterSimpleHome
        }
    }

    private fun setupAdapterCallbacks() {
        adapterSimpleHome?.setAdapterSimpleHomeCallbacks(object : AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks {
            override fun onItemClicked(panel: Panel, position: Int, view: View) {
                when (panel.titleResId) {
                    R.string.songs -> {
                        openFragment(Songs.newInstance(), Songs.TAG)
                    }
                    R.string.albums -> {
                        openFragment(Albums.newInstance(), Albums.TAG)
                    }
                    R.string.artists -> {
                        openFragment(Artists.newInstance(), Artists.TAG)
                    }
                    R.string.genres -> {
                        openFragment(Genres.newInstance(), Genres.TAG)
                    }
                    R.string.folders -> {
                        openFragment(Folders.newInstance(), Folders.TAG)
                    }
                    R.string.folders_hierarchy -> {
                        openFragment(FoldersHierarchy.newInstance(), FoldersHierarchy.TAG)
                    }
                    R.string.recently_added -> {
                        openFragment(RecentlyAdded.newInstance(), RecentlyAdded.TAG)
                    }
                    R.string.recently_played -> {
                        openFragment(RecentlyPlayed.newInstance(), RecentlyPlayed.TAG)
                    }
                    R.string.most_played -> {
                        openFragment(MostPlayed.newInstance(), MostPlayed.TAG)
                    }
                    R.string.year -> {
                        openFragment(Year.newInstance(), Year.TAG)
                    }
                    R.string.preferences -> {
                        openFragment(Preferences.newInstance(), Preferences.TAG)
                    }
                    R.string.playing_queue -> {
                        openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                    }
                    R.string.favorites -> {
                        openFragment(Favorites.newInstance(), Favorites.TAG)
                    }
                    else -> {
                        // Handle other cases or show a message
                    }
                }
            }
        })
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            HomePreferences.HOME_LAYOUT_TYPE -> {
                val layoutType = HomePreferences.getHomeLayoutType()
                adapterSimpleHome?.setLayoutType(layoutType)
                updateGridLayoutSpanCount()
                binding.recyclerView.scheduleLayoutAnimation()
                adapterSimpleHome?.notifyItemRangeChanged(0, adapterSimpleHome?.itemCount ?: 0)
            }
        }
    }

    private fun updateGridLayoutSpanCount() {
        val spanCount = if (HomePreferences.getHomeLayoutType() == CommonPreferencesConstants.GRID_TYPE_GRID) {
            resources.getInteger(R.integer.home_grid_span_count)
        } else {
            1
        }

        gridLayoutManager?.spanCount = spanCount
    }

    companion object {
        fun newInstance(): SimpleHome {
            val args = Bundle()
            val fragment = SimpleHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SimpleHome"
    }
}
