package app.simple.felicity.ui.main.artists

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.ArtistDetailsAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.itemdecorations.SongHolderSpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.main.albums.AlbumPage
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
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: ArtistPage for artist: ${artist.name}")

        artistViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${artist.name}, count: ${data.songs}")
            val adapter = ArtistDetailsAdapter(data, artist)
            binding.recyclerView.addItemDecoration(SongHolderSpacingItemDecoration(48, AppearancePreferences.getListSpacing().toInt()))
            binding.recyclerView.adapter = adapter

            adapter.setArtistAdapterListener(object : GeneralAdapterCallbacks {
                override fun onSongClicked(songs: List<Song>, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in artist: ${artist.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onPlayClicked(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for artist: ${artist.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClicked(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for artist: ${artist.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
                }

                override fun onArtistClicked(artist: Artist, position: Int, view: View) {
                    Log.i(TAG, "onArtistClicked: Artist clicked: ${artist.name}")
                    openFragment(newInstance(artist), TAG)
                }

                override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                    val album = albums[position]
                    Log.i(TAG, "onAlbumClicked: Album clicked: ${album.name}")
                    openFragment(AlbumPage.newInstance(album), AlbumPage.TAG)
                }

                override fun onMenuClicked(view: View) {
                    Log.i(TAG, "onMenuClicked: Menu clicked for artist: ${artist.name}")

                    PopupArtistMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> {
                                        Log.i(TAG, "onMenuItemClick: Play clicked for artist: ${artist.name}")
                                        setMediaItems(data.songs, 0)
                                    }
                                    R.string.shuffle -> {
                                        Log.i(TAG, "onMenuItemClick: Shuffle clicked for artist: ${artist.name}")

                                    }
                                    R.string.send -> {
                                        Log.i(TAG, "onMenuItemClick: Send clicked for artist: ${artist.name}")
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