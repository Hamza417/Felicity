package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterSongs
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentRecentlyAddedBinding
import app.simple.felicity.databinding.HeaderRecentlyAddedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.RecentlyAddedPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.RecentlyAddedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecentlyAdded : PanelFragment() {

    private lateinit var binding: FragmentRecentlyAddedBinding
    private lateinit var headerBinding: HeaderRecentlyAddedBinding

    private var adapterSongs: AdapterSongs? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val recentlyAddedViewModel: RecentlyAddedViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRecentlyAddedBinding.inflate(inflater, container, false)
        headerBinding = HeaderRecentlyAddedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.recyclerView.attachSlideFastScroller()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        gridLayoutManager = GridLayoutManager(requireContext(), RecentlyAddedPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(RecentlyAddedPreferences.getGridType(), RecentlyAddedPreferences.getGridSize())

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                recentlyAddedViewModel.songs.collect { songs ->
                    if (songs.isNotEmpty()) {
                        updateSongsList(songs)
                    } else if (adapterSongs != null) {
                        updateSongsList(songs)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.gridSize.setGridSizeValue(RecentlyAddedPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(RecentlyAddedPreferences.getGridType())

        headerBinding.shuffle.setOnClickListener {
            val songs = recentlyAddedViewModel.songs.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.gridSize.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.one, R.string.two, R.string.three,
                                       R.string.four, R.string.five, R.string.six),
                    menuIcons = listOf(R.drawable.ic_one_16, R.drawable.ic_two_16dp,
                                       R.drawable.ic_three_16dp, R.drawable.ic_four_16dp,
                                       R.drawable.ic_five_16dp, R.drawable.ic_six_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> RecentlyAddedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                        }
                    },
                    onDismiss = {}
            ).show()
        }

        headerBinding.gridType.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.list, R.string.grid),
                    menuIcons = listOf(R.drawable.ic_list_16dp, R.drawable.ic_grid_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.list -> RecentlyAddedPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> RecentlyAddedPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    private fun updateSongsList(songs: List<Audio>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterSongs(songs)
            adapterSongs?.setHasStableIds(true)
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, view: View) {
                    openSongsMenu(audios, position, view as ImageView)
                }
            })
            binding.recyclerView.adapter = adapterSongs
        } else {
            adapterSongs?.updateSongs(songs)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterSongs
            }
        }

        headerBinding.count.text = getString(R.string.x_songs, songs.size)
        headerBinding.hours.text = songs.sumOf { it.duration }
            .toHighlightedTimeString(ThemeManager.theme.textViewTheme.tertiaryTextColor)
        headerBinding.gridSize.setGridSizeValue(RecentlyAddedPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(RecentlyAddedPreferences.getGridType())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            RecentlyAddedPreferences.GRID_SIZE_PORTRAIT, RecentlyAddedPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(RecentlyAddedPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = RecentlyAddedPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            RecentlyAddedPreferences.GRID_TYPE_PORTRAIT, RecentlyAddedPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(RecentlyAddedPreferences.getGridType(), RecentlyAddedPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(RecentlyAddedPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        fun newInstance(): RecentlyAdded {
            val args = Bundle()
            val fragment = RecentlyAdded()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "RecentlyAdded"
    }
}