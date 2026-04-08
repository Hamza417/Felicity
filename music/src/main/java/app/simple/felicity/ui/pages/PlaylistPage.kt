package app.simple.felicity.ui.pages

/**
 * Fragment that displays the playlist page, showing all constituent songs alongside
 * aggregated albums, artists, and genres derived from those songs.
 *
 * <p>Observes [PlaylistViewerViewModel] and updates the UI reactively whenever the
 * playlist's song membership changes. All user interactions are dispatched through
 * [GeneralAdapterCallbacks].</p>
 *
 * @author Hamza417
 */

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.PlaylistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistPage : MediaFragment() {

    private lateinit var binding: FragmentPageArtistBinding
    private var pageAdapter: PageAdapter? = null

    private val playlist: Playlist by lazy {
        requireArguments().parcelable(BundleConstants.PLAYLIST)
            ?: throw IllegalArgumentException("Playlist is required")
    }

    private val playlistViewerViewModel: PlaylistViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<PlaylistViewerViewModel.Factory> {
                    it.create(playlist = playlist)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        Log.d(TAG, "onViewCreated: PlaylistPage for playlist: '${playlist.name}', adapter=${pageAdapter != null}")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                playlistViewerViewModel.data.collect { data ->
                    data?.let { updatePlaylistPage(it) }
                }
            }
        }
    }

    override fun onDestroyView() {
        pageAdapter = null
        super.onDestroyView()
    }

    /**
     * Updates the playlist page UI with the given [PageData].
     *
     * <p>Creates a new [PageAdapter] on the first call, or updates the existing one with
     * fresh data. Re-attaches the adapter to the RecyclerView if the reference was lost
     * during navigation.</p>
     *
     * @param data The [PageData] containing the playlist's songs, albums, artists, and genres.
     */
    private fun updatePlaylistPage(data: PageData) {
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        binding.recyclerView.addItemDecorationSafely(
                PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))

        if (pageAdapter == null) {
            Log.d(TAG, "updatePlaylistPage: Creating new adapter")
            pageAdapter = PageAdapter(data, PageAdapter.PageType.PlaylistPage(playlist))
            binding.recyclerView.adapter = pageAdapter
            setupAdapterCallbacks()
        } else {
            Log.d(TAG, "updatePlaylistPage: Updating existing adapter with new data")
            pageAdapter?.updateData(data)

            if (binding.recyclerView.adapter == null) {
                Log.d(TAG, "updatePlaylistPage: Re-attaching adapter to RecyclerView")
                binding.recyclerView.adapter = pageAdapter
            }
        }

        requireView().startTransitionOnPreDraw()
    }

    /**
     * Registers all interaction callbacks on the [PageAdapter] via [GeneralAdapterCallbacks].
     *
     * <p>Handles song clicks, long-clicks, play/shuffle actions, artist/album/genre navigation,
     * and the overflow menu for the current playlist.</p>
     */
    private fun setupAdapterCallbacks() {
        pageAdapter?.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                Log.i(TAG, "onSongClick: Song clicked in playlist: '${playlist.name}', position: $position")
                setMediaItems(songs, position)
            }

            override fun onSongLongClicked(songs: List<Audio>, position: Int, imageView: ImageView?) {
                openSongsMenu(songs, position, imageView)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onPlayClick: Play clicked for playlist: '${playlist.name}'")
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                Log.i(TAG, "onShuffleClick: Shuffle clicked for playlist: '${playlist.name}'")
                shuffleMediaItems(audios)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                Log.i(TAG, "onArtistClicked: Artist clicked: ${artists[position].name}")
                openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                Log.i(TAG, "onAlbumClicked: Album clicked: ${albums[position].name}")
                openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
            }

            override fun onGenreClicked(genre: Genre, view: View) {
                Log.i(TAG, "onGenreClicked: Genre clicked: ${genre.name}")
                openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
            }

            override fun onMenuClicked(view: View) {
                Log.i(TAG, "onMenuClicked: Menu clicked for playlist: '${playlist.name}'")

                viewLifecycleOwner.lifecycleScope.launch {
                    val currentData = playlistViewerViewModel.data.value ?: return@launch

                    PopupArtistMenu(
                            container = requireContainerView(),
                            anchorView = view,
                            menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                            onMenuItemClick = {
                                when (it) {
                                    R.string.play -> {
                                        Log.i(TAG, "onMenuItemClick: Play clicked for playlist: '${playlist.name}'")
                                        setMediaItems(currentData.songs.toMutableList(), 0)
                                    }
                                    R.string.shuffle -> {
                                        Log.i(TAG, "onMenuItemClick: Shuffle clicked for playlist: '${playlist.name}'")
                                        shuffleMediaItems(currentData.songs)
                                    }
                                    R.string.send -> {
                                        Log.i(TAG, "onMenuItemClick: Send clicked for playlist: '${playlist.name}'")
                                        val audioUris = currentData.songs.map { audio ->
                                            java.io.File(audio.path).toUri()
                                        }

                                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            setType("audio/*")
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(audioUris))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        startActivity(Intent.createChooser(shareIntent, getString(R.string.send)))
                                    }
                                }
                            },
                            menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_send),
                            onDismiss = { Log.d(TAG, "PopupArtistMenu dismissed") }
                    ).show()
                }
            }
        })
    }

    companion object {
        const val TAG = "PlaylistPage"

        /**
         * Creates a new instance of [PlaylistPage] with the given [playlist] bundled as arguments.
         *
         * @param playlist The [Playlist] whose data will be displayed in this fragment.
         * @return A new [PlaylistPage] instance ready to be committed via a fragment transaction.
         */
        fun newInstance(playlist: Playlist): PlaylistPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.PLAYLIST, playlist)
            val fragment = PlaylistPage()
            fragment.arguments = args
            return fragment
        }
    }
}

