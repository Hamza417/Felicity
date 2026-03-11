package app.simple.felicity.ui.panels

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
import app.simple.felicity.databinding.FragmentFavoritesBinding
import app.simple.felicity.databinding.HeaderFavoritesBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.viewmodels.panels.FavoritesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class Favorites : MediaFragment() {

    private lateinit var binding: FragmentFavoritesBinding
    private lateinit var headerBinding: HeaderFavoritesBinding

    private var adapterSongs: AdapterSongs? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val favoritesViewModel: FavoritesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        headerBinding = HeaderFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        gridLayoutManager = GridLayoutManager(requireContext(), SongsPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        // binding.recyclerView.setGridType(SongsPreferences.getGridType(), SongsPreferences.getGridSize())

        headerBinding.shuffle.setOnClickListener {
            val songs = favoritesViewModel.favorites.value
            if (songs.isNotEmpty()) shuffleMediaItems(songs)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoritesViewModel.favorites.collect { songs ->
                    updateFavoritesList(songs)
                }
            }
        }
    }

    override fun onDestroyView() {
        adapterSongs = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun updateFavoritesList(songs: List<Audio>) {
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
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        adapterSongs?.currentlyPlayingSong = audio
    }

    companion object {
        const val TAG = "Favorites"

        fun newInstance(): Favorites {
            val args = Bundle()

            val fragment = Favorites()
            fragment.arguments = args
            return fragment
        }
    }
}