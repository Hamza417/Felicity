package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterMostPlayed
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentMostPlayedBinding
import app.simple.felicity.databinding.HeaderMostPlayedBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.app.TotalTime.Companion.showTotalTime
import app.simple.felicity.dialogs.mostplayed.MostPlayedMenu.Companion.showMostPlayedMenu
import app.simple.felicity.extensions.fragments.BasePanelFragment
import app.simple.felicity.preferences.MostPlayedPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.AudioWithStat
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.viewmodels.panels.MostPlayedViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Panel fragment displaying the user's most frequently played songs, ordered by play count
 * descending. Each item shows the total number of times the song has been played. The list
 * is backed by the {@code song_stats} table and refreshes reactively as play counts change.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class MostPlayed : BasePanelFragment() {

    private lateinit var binding: FragmentMostPlayedBinding
    private lateinit var headerBinding: HeaderMostPlayedBinding

    private var adapterSongs: AdapterMostPlayed? = null

    private val mostPlayedViewModel: MostPlayedViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMostPlayedBinding.inflate(inflater, container, false)
        headerBinding = HeaderMostPlayedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.recyclerView.attachSlideFastScroller()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        binding.recyclerView.setupGridLayoutManager(MostPlayedPreferences.getGridSize().spanCount)

        setupClickListeners()

        mostPlayedViewModel.songs.collectListWhenStarted({ adapterSongs != null }) { songs ->
            updateSongsList(songs)
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.shuffle.setOnClickListener {
            val songs = mostPlayedViewModel.songs.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs.map { it.audio })
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.menu.setOnClickListener {
            childFragmentManager.showMostPlayedMenu()
        }
    }

    private fun updateSongsList(songs: List<AudioWithStat>) {
        if (adapterSongs == null) {
            adapterSongs = AdapterMostPlayed(initial = songs)
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
        headerBinding.hours.text = songs.sumOf { it.audio.duration }.toDynamicTimeString()

        headerBinding.hours.setOnClickListener {
            childFragmentManager.showTotalTime(
                    totalTime = songs.sumOf { it.audio.duration },
                    count = songs.size
            )
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            MostPlayedPreferences.GRID_SIZE_PORTRAIT, MostPlayedPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = MostPlayedPreferences.getGridSize()
                adapterSongs?.layoutMode = newMode
                applyGridSizeUpdate(binding.recyclerView, newMode.spanCount)
            }
        }
    }

    companion object {
        fun newInstance(): MostPlayed {
            val args = Bundle()
            val fragment = MostPlayed()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "MostPlayed"
    }
}
