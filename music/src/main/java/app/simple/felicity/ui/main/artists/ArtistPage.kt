package app.simple.felicity.ui.main.artists

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.adapters.ui.page.ArtistDetailsAdapter
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Song
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.artists.ArtistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class ArtistPage : MediaFragment() {

    private lateinit var binding: FragmentPageArtistBinding

    private val artistViewerViewModel: ArtistViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<ArtistViewerViewModel.Factory>() {
                    it.create(artist = artist)
                }
            }
    )

    private val artist: Artist by lazy {
        requireArguments().parcelable(BundleConstants.ARTIST)
            ?: throw IllegalArgumentException("Artist is required")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated: ArtistPage for artist: ${artist.name}")

        artistViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${artist.name}, count: ${data.songs}")
            val adapter = ArtistDetailsAdapter(data, artist)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter

            adapter.setArtistAdapterListener(object : ArtistDetailsAdapter.Companion.ArtistSongsAdapterListener {
                override fun onSongClick(songs: List<Song>, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in artist: ${artist.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onPlayClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for artist: ${artist.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for artist: ${artist.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
                }

                override fun onArtistClicked(artist: Artist) {
                    Log.i(TAG, "onArtistClicked: Artist clicked: ${artist.name}")
                    openFragment(newInstance(artist), TAG)
                }
            })
        }
    }

    companion object {
        const val TAG = "ArtistPage"

        fun newInstance(artist: Artist): ArtistPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.ARTIST, artist)
            val fragment = ArtistPage()
            fragment.arguments = args
            return fragment
        }
    }
}