package app.simple.felicity.extensions.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.PageAdapter
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration
import app.simple.felicity.decorations.utils.RecyclerViewUtils.addItemDecorationSafely
import app.simple.felicity.dialogs.pages.PageSortDialog.Companion.showPageSortDialog
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.preferences.PagePreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.PageData
import app.simple.felicity.repository.sort.PageSort
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.pages.GenrePage
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base fragment shared by all page panels:
 * [app.simple.felicity.ui.pages.AlbumPage], [app.simple.felicity.ui.pages.ArtistPage],
 * [app.simple.felicity.ui.pages.GenrePage], [app.simple.felicity.ui.pages.FolderPage],
 * [app.simple.felicity.ui.pages.YearPage], and [app.simple.felicity.ui.pages.PlaylistPage].
 *
 * Centralizes the [app.simple.felicity.adapters.ui.page.PageAdapter] lifecycle,
 * [app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration] setup,
 * and the common [app.simple.felicity.callbacks.GeneralAdapterCallbacks] (song playback,
 * long-click menu, play/shuffle buttons, and cross-page navigation to artist/album/genre pages).
 *
 * Subclasses are responsible for:
 * - Inflating and exposing their binding's [androidx.recyclerview.widget.RecyclerView] via [pageRecyclerView].
 * - Supplying the correct [app.simple.felicity.adapters.ui.page.PageAdapter.PageType] via [pageType].
 * - Calling [collectPageData] in [onViewCreated] with their ViewModel's [kotlinx.coroutines.flow.StateFlow].
 * - Implementing [onMenuClicked] to display the page-specific overflow popup.
 * - Implementing [resortPageData] to trigger a re-sort on the page's ViewModel.
 *
 * @author Hamza417
 */
abstract class BasePageFragment : MediaFragment() {

    /** The [app.simple.felicity.adapters.ui.page.PageAdapter] backing the page's RecyclerView. Cleared automatically in [onDestroyView]. */
    protected var pageAdapter: PageAdapter? = null

    /**
     * The [androidx.recyclerview.widget.RecyclerView] rendered by this page. Typically sourced
     * directly from the fragment's view binding after inflation.
     */
    protected abstract val pageRecyclerView: RecyclerView

    /**
     * Identifies the specific page type and supplies the model needed to build the
     * [PageAdapter] header and sections.
     */
    protected abstract val pageType: PageAdapter.PageType

    /**
     * Invoked when the user taps the overflow menu button in the page header.
     * Each subclass shows its own popup with the appropriate action items.
     *
     * @param view The anchor [android.view.View] for the popup.
     */
    protected abstract fun onMenuClicked(view: View)

    /**
     * Called when a page sort preference changes. Each subclass delegates to its
     * ViewModel's [resort][app.simple.felicity.viewmodels.viewer.AlbumViewerViewModel.resort]
     * method so that the song list is re-ordered without an additional database trip.
     */
    protected abstract fun resortPageData()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pageRecyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()
    }

    override fun onDestroyView() {
        pageAdapter = null
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if (key != null && key in PagePreferences.ALL_PAGE_PREF_KEYS) {
            resortPageData()
        }
    }

    /**
     * Collects [app.simple.felicity.repository.models.PageData] emissions from [dataFlow] on
     * the [androidx.lifecycle.Lifecycle.State.CREATED] lifecycle and forwards each non-null
     * value to [onPageData]. Call this once from [onViewCreated], passing your ViewModel's
     * data [kotlinx.coroutines.flow.StateFlow].
     *
     * @param dataFlow A lambda returning the [kotlinx.coroutines.flow.StateFlow] to observe.
     */
    protected fun collectPageData(dataFlow: () -> StateFlow<PageData?>) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                dataFlow().collect { data ->
                    data?.let { onPageData(it) }
                }
            }
        }
    }

    /**
     * Applies [app.simple.felicity.decorations.itemdecorations.PageSpacingItemDecoration]
     * (only once, guarded by class-type check), creates or updates the [PageAdapter], and
     * starts the shared-element transition. Invoked for every new [PageData] emission.
     *
     * @param data The latest [PageData] with songs, albums, artists, and genres.
     */
    private fun onPageData(data: PageData) {
        val horPad = resources.getDimensionPixelSize(R.dimen.padding_10)
        pageRecyclerView.addItemDecorationSafely(
                PageSpacingItemDecoration(horPad, AppearancePreferences.getListSpacing().toInt()))

        if (pageAdapter == null) {
            pageAdapter = PageAdapter(data, pageType)
            pageRecyclerView.adapter = pageAdapter
            setupAdapterCallbacks()
        } else {
            pageAdapter?.updateData(data)
            if (pageRecyclerView.adapter == null) {
                pageRecyclerView.adapter = pageAdapter
            }
        }

        requireView().startTransitionOnPreDraw()
    }

    /**
     * Returns the [PageSort] page-type string constant that matches the current [pageType].
     * Used when opening the [app.simple.felicity.dialogs.pages.PageSortDialog].
     */
    private fun resolvePageTypeKey(): String {
        return when (pageType) {
            is PageAdapter.PageType.AlbumPage -> PageSort.PAGE_TYPE_ALBUM
            is PageAdapter.PageType.ArtistPage -> PageSort.PAGE_TYPE_ARTIST
            is PageAdapter.PageType.GenrePage -> PageSort.PAGE_TYPE_GENRE
            is PageAdapter.PageType.FolderPage -> PageSort.PAGE_TYPE_FOLDER
            is PageAdapter.PageType.YearPage -> PageSort.PAGE_TYPE_YEAR
            is PageAdapter.PageType.PlaylistPage -> PageSort.PAGE_TYPE_PLAYLIST
        }
    }

    /**
     * Registers all shared [app.simple.felicity.callbacks.GeneralAdapterCallbacks] on the
     * [PageAdapter]. Navigation callbacks (artist, album, genre) and playback callbacks
     * (song click, play, shuffle) are identical across all page types. The menu callback
     * delegates to [onMenuClicked]; the sort callback opens [app.simple.felicity.dialogs.pages.PageSortDialog].
     */
    private fun setupAdapterCallbacks() {
        pageAdapter?.setArtistAdapterListener(object : GeneralAdapterCallbacks {
            override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                setMediaItems(songs, position)
            }

            override fun onSongLongClicked(songs: List<Audio>, position: Int, imageView: ImageView?) {
                openSongsMenu(songs, position, imageView)
            }

            override fun onPlayClicked(audios: MutableList<Audio>, position: Int) {
                setMediaItems(audios, position)
            }

            override fun onShuffleClicked(audios: MutableList<Audio>, position: Int) {
                shuffleMediaItems(audios)
            }

            override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                openFragment(ArtistPage.Companion.newInstance(artists[position]), ArtistPage.Companion.TAG)
            }

            override fun onAlbumClicked(albums: List<Album>, position: Int, view: View) {
                openFragment(AlbumPage.Companion.newInstance(albums[position]), AlbumPage.Companion.TAG)
            }

            override fun onGenreClicked(genre: Genre, view: View) {
                openFragment(GenrePage.Companion.newInstance(genre), GenrePage.Companion.TAG)
            }

            override fun onMenuClicked(view: View) {
                this@BasePageFragment.onMenuClicked(view)
            }

            override fun onSortClicked(view: View) {
                childFragmentManager.showPageSortDialog(resolvePageTypeKey())
            }
        })
    }
}