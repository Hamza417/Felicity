package app.simple.felicity.ui.main.albums

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.AlbumDetailsAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.main.artists.ArtistPage
import app.simple.felicity.ui.main.genres.GenrePage
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.albums.AlbumViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class AlbumPage : MediaFragment() {

    private lateinit var binding: FragmentPageArtistBinding

    private val albumViewerViewModel: AlbumViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AlbumViewerViewModel.Factory>() {
                    it.create(album = album)
                }
            }
    )

    private val album: Album by lazy {
        requireArguments().parcelable(BundleConstants.ALBUM)
            ?: throw IllegalArgumentException("Artist is required")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: ArtistPage for artist: ${album.name}")

        albumViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${album.name}, count: ${data.songs}")
            val adapter = AlbumDetailsAdapter(data, album)
            // binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter

            adapter.setArtistAdapterListener(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: List<Song>, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in artist: ${album.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onPlayClicked(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for artist: ${album.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClicked(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for artist: ${album.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
                }

                override fun onArtistClicked(artist: Artist) {
                    Log.i(TAG, "onArtistClicked: Artist clicked: ${artist.name}")
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                }

                override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                    openFragment(newInstance(albums[position]), TAG)
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    Log.i(TAG, "onGenreClicked: Genre clicked: ${genre.name}")
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }

                override fun onMenuClicked(view: View) {
                    Log.i(TAG, "onMenuClicked: Menu clicked for artist: ${album.name}")

                    PopupArtistMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> {
                                        Log.i(TAG, "onMenuItemClick: Play clicked for artist: ${album.name}")
                                        setMediaItems(data.songs, 0)
                                    }
                                    R.string.shuffle -> {
                                        Log.i(TAG, "onMenuItemClick: Shuffle clicked for artist: ${album.name}")
                                    }
                                    R.string.send -> {
                                        Log.i(TAG, "onMenuItemClick: Send clicked for artist: ${album.name}")
                                        val songUris = data.songs.mapNotNull { song ->
                                            song.uri
                                        }

                                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "audio/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(songUris))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        startActivity(Intent.createChooser(shareIntent, "Share Songs"))
                                    }
                                }
                            },
                            menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_send),
                            onDismiss = { Log.d(TAG, "PopupArtistMenu dismissed") }
                    ).show()
                }
            })

            requireView().startTransitionOnPreDraw()
        }
    }

    companion object {
        const val TAG = "AlbumPage"

        fun newInstance(album: Album): AlbumPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.ALBUM, album)
            val fragment = AlbumPage()
            fragment.arguments = args
            return fragment
        }
    }
}