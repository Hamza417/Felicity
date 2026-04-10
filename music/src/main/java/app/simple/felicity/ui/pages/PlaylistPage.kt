package app.simple.felicity.ui.pages

/**
 * Fragment that displays the playlist page, showing all constituent songs alongside
 * aggregated albums, artists, and genres derived from those songs.
 *
 * Observes [PlaylistViewerViewModel] and updates the UI reactively whenever the
 * playlist's song membership changes. All common interaction callbacks are handled
 * by [app.simple.felicity.extensions.fragments.BasePageFragment]; only the playlist-specific overflow menu is implemented here.
 *
 * @author Hamza417
 */

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Playlist
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.PlaylistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlaylistPage : BasePageFragment() {

    private lateinit var binding: FragmentPageArtistBinding

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

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.PlaylistPage(playlist) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Sets up the RecyclerView and begins collecting [PageData] from [PlaylistViewerViewModel].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { playlistViewerViewModel.data }
    }

    /**
     * Delegates sort re-ordering to the [PlaylistViewerViewModel] without a database round-trip.
     */
    override fun resortPageData() {
        playlistViewerViewModel.resort()
    }

    /**
     * Displays the playlist overflow menu with play, shuffle, and send actions.
     *
     * @param view The anchor [View] for the popup.
     */
    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = playlistViewerViewModel.data.value ?: return@launch

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
                            R.string.send -> {
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
                    onDismiss = {}
            ).show()
        }
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
