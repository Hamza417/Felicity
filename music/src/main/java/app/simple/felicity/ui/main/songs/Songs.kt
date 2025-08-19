package app.simple.felicity.ui.main.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.lists.songs.SongsAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.decorations.itemanimators.FlipItemAnimator
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.dialogs.songs.SongsMenu.Companion.showSongsMenu
import app.simple.felicity.dialogs.songs.SongsSort.Companion.showSongsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Song
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class Songs : MediaFragment() {

    private lateinit var binding: FragmentSongsBinding

    private var songsAdapter: SongsAdapter? = null

    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        binding = FragmentSongsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            songsAdapter = SongsAdapter(it)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = songsAdapter
            binding.recyclerView.itemAnimator = FlipItemAnimator()

            songsAdapter?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: List<Song>, position: Int, view: View?) {
                    setMediaItems(songs, position)
                }

                override fun onMenuClicked(view: View) {
                    super.onMenuClicked(view)
                    parentFragmentManager.showSongsMenu()
                }

                override fun onFilterClicked(view: View) {
                    childFragmentManager.showSongsSort()
                }
            })
        }
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        songsAdapter?.currentlyPlayingSong = song
    }

    companion object {
        const val TAG = "Songs"

        fun newInstance(): Songs {
            val args = Bundle()
            val fragment = Songs()
            fragment.arguments = args
            return fragment
        }
    }
}
