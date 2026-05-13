package app.simple.felicity.ui.pages

/**
 * Fragment that displays the composer page, showing all related data such as
 * songs, albums, and genres associated with a given composer (represented as an [Artist]).
 *
 * This fragment observes [ComposerViewerViewModel] and updates the UI reactively
 * whenever new [PageData] is emitted. All common interaction callbacks are handled
 * by [app.simple.felicity.extensions.fragments.BasePageFragment]; only the
 * composer-specific overflow menu is implemented here.
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
import app.simple.felicity.databinding.FragmentPageComposerBinding
import app.simple.felicity.decorations.views.PopupMenuItem
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.extensions.fragments.BasePageFragment
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.viewer.ComposerViewerViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ComposerPage : BasePageFragment() {

    private lateinit var binding: FragmentPageComposerBinding

    private val composer: Artist by lazy {
        requireArguments().parcelable(BundleConstants.COMPOSER)
            ?: throw IllegalArgumentException("Composer is required")
    }

    private val composerViewerViewModel: ComposerViewerViewModel by viewModels(
            ownerProducer = { this },
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<ComposerViewerViewModel.Factory>() {
                    it.create(composer = composer)
                }
            }
    )

    override val pageRecyclerView: RecyclerView
        get() = binding.recyclerView

    override val pageType: PageAdapter.PageType by lazy { PageAdapter.PageType.ComposerPage(composer) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPageComposerBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Sets up the RecyclerView and begins collecting [PageData] from [ComposerViewerViewModel].
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collectPageData { composerViewerViewModel.data }
    }

    /**
     * Delegates sort re-ordering to the [ComposerViewerViewModel] without a database round-trip.
     */
    override fun resortPageData() {
        composerViewerViewModel.resort()
    }

    /**
     * Displays the composer overflow menu with play, shuffle, and send actions.
     *
     * @param view The anchor [View] for the popup.
     */
    override fun onMenuClicked(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentData = composerViewerViewModel.data.value ?: return@launch

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
        const val TAG = "ComposerPage"

        /**
         * Creates a new instance of [ComposerPage] with the given [composer] bundled as arguments.
         * Song paths are stripped before parceling to stay well under the Binder transaction limit —
         * the ViewModel fetches them fresh from the repository anyway.
         *
         * @param composer The composer (as an [Artist]) whose data will be displayed.
         * @return A new [ComposerPage] instance ready to be committed via a fragment transaction.
         */
        fun newInstance(composer: Artist): ComposerPage {
            val args = Bundle()
            args.putParcelable(BundleConstants.COMPOSER, composer.copy(songPaths = emptyList()))
            val fragment = ComposerPage()
            fragment.arguments = args
            return fragment
        }
    }
}

