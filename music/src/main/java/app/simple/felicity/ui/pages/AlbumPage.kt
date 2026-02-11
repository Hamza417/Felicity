package app.simple.felicity.ui.pages

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.AlbumViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

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
            ?: throw IllegalArgumentException("Album is required")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: AlbumPage for album: ${album.name}")

        // Observe StateFlow with proper lifecycle handling
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                albumViewerViewModel.data.collect { data ->
                    data?.let { updateAlbumPage(it) }
                }
            }
        }
    }

    private fun updateAlbumPage(data: PageData) {
        Log.d(TAG, "updateAlbumPage: Updating UI for album: ${album.name} with data")
        val adapter = PageAdapter(data, album)
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecoration(PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))
        binding.recyclerView.adapter = adapter

        adapter.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClick: Audio clicked in album: ${album.name}, position: $position")
                setMediaItems(songs, position)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                openFragment(newInstance(albums[position]), TAG)
            }

            override fun onGenreClicked(genre: Genre, view: View) {
                Log.i(TAG, "onGenreClicked: Genre clicked: ${genre.name}")
                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked for album: ${album.name}")

                PopupArtistMenu(
                        container = requireContainerView(),
                        anchorView = view,
                        menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                        onMenuItemClick = {
                            when (it) {
                                R.string.play -> {
                                    Log.i(TAG, "onMenuItemClick: Play clicked for album: ${album.name}")
                                    setMediaItems(data.songs.toMutableList(), 0)
                                }
                                R.string.shuffle -> {
                                    Log.i(TAG, "onMenuItemClick: Shuffle clicked for album: ${album.name}")
                                    setMediaItems(data.songs.shuffled().toMutableList(), 0)
                                }
                                R.string.send -> {
                                    Log.i(TAG, "onMenuItemClick: Send clicked for album: ${album.name}")
                                    val audioUris = data.songs.map { audio ->
                                        java.io.File(audio.path).toUri()
                                    }

                                    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                        setType("audio/*")
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(audioUris))
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