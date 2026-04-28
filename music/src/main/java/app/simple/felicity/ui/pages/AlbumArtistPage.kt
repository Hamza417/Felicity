package app.simple.felicity.ui.pages

/**
 * Fragment that displays the Album Artist page — shows all songs, albums, and genres
 * that belong to a given album artist (the "album artist" tag, not the "artist" tag).
 *
 * Observation of data is handled by [AlbumArtistViewerViewModel] and the common UI
 * interactions (song clicks, menu, sort) live in [app.simple.felicity.extensions.fragments.BasePageFragment].
 *
 * @author Hamza417
 */

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.databinding.FragmentPageAlbumArtistBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.AlbumArtistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AlbumArtistPage : BasePageFragment() {

    private lateinit var binding: FragmentPageAlbumArtistBinding

    private val albumArtist: Artist by lazy {
        requireArguments().parcelable(BundleConstants.ALBUM_ARTIST)
            ?: throw IllegalArgumentException("Album Artist argument is required")
    }

    private val albumArtistViewerViewModel: AlbumArtistViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<AlbumArtistViewerViewModel.Factory>() {
                    it.create(albumArtist = albumArtist)
                }
            }
    )

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    /**
     * The page header type tells [PageAdapter] to render the artist-style header layout,
     * which shows the artist name, album count, and song count at the top of the list.
     */
    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.ArtistPage(albumArtist) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageAlbumArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { albumArtistViewerViewModel.data }
    }

    /**
     * Re-applies the current sort order to the cached song list without re-fetching
     * anything from the database — quick and cheap.
     */
    override fun resortPageData() {
        albumArtistViewerViewModel.resort()
    }

    /**
     * Shows the album artist overflow menu with play, shuffle, and send actions.
     *
     * @param view The anchor [View] for the popup.
     */
    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = albumArtistViewerViewModel.data.value ?: return@launch

            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(
                            PopupMenuItem(title = R.string.play, icon = R.drawable.ic_play),
                            PopupMenuItem(title = R.string.shuffle, icon = R.drawable.ic_shuffle),
                            PopupMenuItem(title = R.string.send, icon = R.drawable.ic_send)
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.play -> setMediaItems(currentData.songs.toMutableList(), 0)
                            R.string.shuffle -> shuffleMediaItems(currentData.songs)
                            R.string.send -> { /* TODO: Implement send functionality */
                            }
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    companion object {
        const val TAG = "AlbumArtistPage"

        /**
         * Creates a new [AlbumArtistPage] with the given [albumArtist] bundled as arguments.
         *
         * @param albumArtist The [Artist] (acting as album artist) to display.
         * @return A new [AlbumArtistPage] instance ready for a fragment transaction.
         */
        fun newInstance(albumArtist: Artist): AlbumArtistPage {
            val args = Bundle()
            // Strip the song paths before parceling — they can be huge for big libraries
            // and will blow past the 1 MB Binder transaction limit in no time. The
            // ViewModel fetches the songs fresh from the repository anyway.
            args.putParcelable(BundleConstants.ALBUM_ARTIST, albumArtist.copy(songPaths = emptyList()))
            val fragment = AlbumArtistPage()
            fragment.arguments = args
            return fragment
        }
    }
}

