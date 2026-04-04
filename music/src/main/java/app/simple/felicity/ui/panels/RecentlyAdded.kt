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
import app.simple.felicity.adapters.ui.lists.AdapterRecentlyAdded
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentRecentlyAddedBinding
import app.simple.felicity.databinding.HeaderRecentlyAddedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.recentlyadded.RecentlyAddedMenu.Companion.showRecentlyAddedMenu
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.RecentlyAddedPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.viewmodels.panels.RecentlyAddedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecentlyAdded : PanelFragment() {

    private lateinit var binding: FragmentRecentlyAddedBinding
    private lateinit var headerBinding: HeaderRecentlyAddedBinding

    private var adapterSongs: AdapterRecentlyAdded? = null
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

        val mode = RecentlyAddedPreferences.getGridSize()
        gridLayoutManager = GridLayoutManager(requireContext(), mode.spanCount)
        binding.recyclerView.layoutManager = gridLayoutManager

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
        headerBinding.shuffle.setOnClickListener {
            val songs = recentlyAddedViewModel.songs.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showRecentlyAddedMenu()
        }
    }

    private fun updateSongsList(songs: List<Audio>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterRecentlyAdded(songs)
            adapterSongs?.setHasStableIds(true)
            adapterSongs?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(audios: MutableList<Audio>, position: Int, imageView: ImageView?) {
                    openSongsMenu(audios, position, imageView)
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
            .toDynamicTimeString()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            RecentlyAddedPreferences.GRID_SIZE_PORTRAIT, RecentlyAddedPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = RecentlyAddedPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
                adapterSongs?.layoutMode = newMode
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