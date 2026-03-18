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
import app.simple.felicity.databinding.FragmentRecentlyPlayedBinding
import app.simple.felicity.databinding.HeaderRecentlyPlayedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.RecentlyPlayedPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toHighlightedTimeString
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.viewmodels.panels.RecentlyPlayedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panel fragment displaying songs the user has played most recently, ordered by last-played
 * timestamp descending. The list is backed by the {@code song_stats} table and refreshes
 * reactively as new songs are played.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class RecentlyPlayed : PanelFragment() {

    private lateinit var binding: FragmentRecentlyPlayedBinding
    private lateinit var headerBinding: HeaderRecentlyPlayedBinding

    private var adapterSongs: AdapterSongs? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val recentlyPlayedViewModel: RecentlyPlayedViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentRecentlyPlayedBinding.inflate(inflater, container, false)
        headerBinding = HeaderRecentlyPlayedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        gridLayoutManager = GridLayoutManager(requireContext(), RecentlyPlayedPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(RecentlyPlayedPreferences.getGridType(), RecentlyPlayedPreferences.getGridSize())

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                recentlyPlayedViewModel.songs.collect { songs ->
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
        headerBinding.gridSize.setGridSizeValue(RecentlyPlayedPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(RecentlyPlayedPreferences.getGridType())

        headerBinding.shuffle.setOnClickListener {
            val songs = recentlyPlayedViewModel.songs.value
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
                            R.string.one -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> RecentlyPlayedPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
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
                            R.string.list -> RecentlyPlayedPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> RecentlyPlayedPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
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
        headerBinding.gridSize.setGridSizeValue(RecentlyPlayedPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(RecentlyPlayedPreferences.getGridType())
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        adapterSongs?.currentlyPlayingSong = audio
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            RecentlyPlayedPreferences.GRID_SIZE_PORTRAIT, RecentlyPlayedPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(RecentlyPlayedPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = RecentlyPlayedPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            RecentlyPlayedPreferences.GRID_TYPE_PORTRAIT, RecentlyPlayedPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(RecentlyPlayedPreferences.getGridType(), RecentlyPlayedPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(RecentlyPlayedPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        fun newInstance(): RecentlyPlayed {
            val args = Bundle()
            val fragment = RecentlyPlayed()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "RecentlyPlayed"
    }
}

