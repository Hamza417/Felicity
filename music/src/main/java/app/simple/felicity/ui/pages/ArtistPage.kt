package app.simple.felicity.ui.pages

/**
 * Fragment that displays the artist page, showing all related data such as
 * songs, albums, and genres associated with a given [Artist].
 *
 * This fragment observes [ArtistViewerViewModel] and updates the UI reactively
 * whenever new [PageData] is emitted. All common interaction callbacks are
 * handled by [app.simple.felicity.extensions.fragments.BasePageFragment]; only the artist-specific overflow menu is
 * implemented here.
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
import app.simple.felicity.databinding.FragmentPageArtistBinding
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.popups.PopupArtistMenu
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.ArtistViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ArtistPage : BasePageFragment() {

    private lateinit var binding: FragmentPageArtistBinding

    private val artist: Artist by lazy {
        requireArguments().parcelable(BundleConstants.ARTIST)
            ?: throw IllegalArgumentException("Artist is required")
    }

    private val artistViewerViewModel: ArtistViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<ArtistViewerViewModel.Factory>() {
                    it.create(artist = artist)
                }
            }
    )

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.ArtistPage(artist) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageArtistBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Sets up the RecyclerView and begins collecting [PageData] from [ArtistViewerViewModel].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { artistViewerViewModel.data }
    }

    /**
     * Delegates sort re-ordering to the [ArtistViewerViewModel] without a database round-trip.
     */
    override fun resortPageData() {
        artistViewerViewModel.resort()
    }

    /**
     * Displays the artist overflow menu with play, shuffle, and send actions.
     *
     * @param view The anchor [View] for the popup.
     */
    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = artistViewerViewModel.data.value ?: return@launch

            PopupArtistMenu(
                    container = requireContainerView(),
                    anchorView = view,
                    menuItems = listOf(R.string.play, R.string.shuffle, R.string.send),
                    onMenuItemClick = {
                        when (it) {
                            R.string.play -> setMediaItems(currentData.songs.toMutableList(), 0)
                            R.string.shuffle -> shuffleMediaItems(currentData.songs)
                            R.string.send -> { /* TODO: Implement send functionality */
                            }
                        }
                    },
                    menuIcons = listOf(R.drawable.ic_play, R.drawable.ic_shuffle, R.drawable.ic_send),
                    onDismiss = {}
            ).show()
        }
    }


    companion object {
        const val TAG = "ArtistPage"

        /**
         * Creates a new instance of [ArtistPage] with the given [artist] bundled as arguments.
         *
         * @param artist The [Artist] whose data will be displayed in this fragment.
         * @return A new [ArtistPage] instance ready to be committed via a fragment transaction.
         */
        fun newInstance(artist: Artist): ArtistPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.ARTIST, artist)
            val fragment = ArtistPage()
            fragment.arguments = args
            return fragment
        }
    }
}