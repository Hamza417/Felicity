package app.simple.felicity.dialogs.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortPageBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.PagePreferences
import app.simple.felicity.repository.sort.PageSort

/**
 * Bottom-sheet dialog for selecting the sort field and sort order for a specific viewer page.
 *
 * Each page type (album, artist, genre, folder, year, playlist) stores its preferences
 * independently via [PagePreferences]. The [PAGE_TYPE] bundle argument determines which
 * preference keys to read and write. Only the Album page shows the Track Number chip,
 * since track order is meaningful only within a single album context.
 *
 * @author Hamza417
 */
class PageSortDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortPageBinding

    private val pageType: String by lazy {
        requireArguments().getString(PageSort.PAGE_TYPE, PageSort.PAGE_TYPE_ALBUM)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSortPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (pageType == PageSort.PAGE_TYPE_ALBUM) {
            binding.trackNumber.visibility = View.VISIBLE
        }

        val currentSort = getCurrentSort()
        val currentOrder = getCurrentOrder()

        when (currentSort) {
            CommonPreferencesConstants.BY_TITLE -> binding.title.isChecked = true
            CommonPreferencesConstants.BY_ARTIST -> binding.artist.isChecked = true
            CommonPreferencesConstants.BY_ALBUM -> binding.album.isChecked = true
            CommonPreferencesConstants.BY_PATH -> binding.path.isChecked = true
            CommonPreferencesConstants.BY_DATE_ADDED -> binding.dateAdded.isChecked = true
            CommonPreferencesConstants.BY_DATE_MODIFIED -> binding.dateModified.isChecked = true
            CommonPreferencesConstants.BY_DURATION -> binding.duration.isChecked = true
            CommonPreferencesConstants.BY_YEAR -> binding.year.isChecked = true
            CommonPreferencesConstants.BY_TRACK_NUMBER -> {
                if (pageType == PageSort.PAGE_TYPE_ALBUM) binding.trackNumber.isChecked = true
                else binding.title.isChecked = true
            }
            CommonPreferencesConstants.BY_COMPOSER -> binding.composer.isChecked = true
        }

        binding.normal.isChecked = currentOrder == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = currentOrder == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.title.id -> setSort(CommonPreferencesConstants.BY_TITLE)
                binding.artist.id -> setSort(CommonPreferencesConstants.BY_ARTIST)
                binding.album.id -> setSort(CommonPreferencesConstants.BY_ALBUM)
                binding.path.id -> setSort(CommonPreferencesConstants.BY_PATH)
                binding.dateAdded.id -> setSort(CommonPreferencesConstants.BY_DATE_ADDED)
                binding.dateModified.id -> setSort(CommonPreferencesConstants.BY_DATE_MODIFIED)
                binding.duration.id -> setSort(CommonPreferencesConstants.BY_DURATION)
                binding.year.id -> setSort(CommonPreferencesConstants.BY_YEAR)
                binding.trackNumber.id -> setSort(CommonPreferencesConstants.BY_TRACK_NUMBER)
                binding.composer.id -> setSort(CommonPreferencesConstants.BY_COMPOSER)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> setOrder(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> setOrder(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    private fun getCurrentSort(): Int {
        return when (pageType) {
            PageSort.PAGE_TYPE_ALBUM -> PagePreferences.getAlbumSort()
            PageSort.PAGE_TYPE_ARTIST -> PagePreferences.getArtistSort()
            PageSort.PAGE_TYPE_GENRE -> PagePreferences.getGenreSort()
            PageSort.PAGE_TYPE_FOLDER -> PagePreferences.getFolderSort()
            PageSort.PAGE_TYPE_YEAR -> PagePreferences.getYearSort()
            PageSort.PAGE_TYPE_PLAYLIST -> PagePreferences.getPlaylistSort()
            else -> CommonPreferencesConstants.BY_TITLE
        }
    }

    private fun getCurrentOrder(): Int {
        return when (pageType) {
            PageSort.PAGE_TYPE_ALBUM -> PagePreferences.getAlbumOrder()
            PageSort.PAGE_TYPE_ARTIST -> PagePreferences.getArtistOrder()
            PageSort.PAGE_TYPE_GENRE -> PagePreferences.getGenreOrder()
            PageSort.PAGE_TYPE_FOLDER -> PagePreferences.getFolderOrder()
            PageSort.PAGE_TYPE_YEAR -> PagePreferences.getYearOrder()
            PageSort.PAGE_TYPE_PLAYLIST -> PagePreferences.getPlaylistOrder()
            else -> CommonPreferencesConstants.ASCENDING
        }
    }

    private fun setSort(value: Int) {
        when (pageType) {
            PageSort.PAGE_TYPE_ALBUM -> PagePreferences.setAlbumSort(value)
            PageSort.PAGE_TYPE_ARTIST -> PagePreferences.setArtistSort(value)
            PageSort.PAGE_TYPE_GENRE -> PagePreferences.setGenreSort(value)
            PageSort.PAGE_TYPE_FOLDER -> PagePreferences.setFolderSort(value)
            PageSort.PAGE_TYPE_YEAR -> PagePreferences.setYearSort(value)
            PageSort.PAGE_TYPE_PLAYLIST -> PagePreferences.setPlaylistSort(value)
        }
    }

    private fun setOrder(value: Int) {
        when (pageType) {
            PageSort.PAGE_TYPE_ALBUM -> PagePreferences.setAlbumOrder(value)
            PageSort.PAGE_TYPE_ARTIST -> PagePreferences.setArtistOrder(value)
            PageSort.PAGE_TYPE_GENRE -> PagePreferences.setGenreOrder(value)
            PageSort.PAGE_TYPE_FOLDER -> PagePreferences.setFolderOrder(value)
            PageSort.PAGE_TYPE_YEAR -> PagePreferences.setYearOrder(value)
            PageSort.PAGE_TYPE_PLAYLIST -> PagePreferences.setPlaylistOrder(value)
        }
    }

    companion object {
        private const val TAG = "PageSortDialog"

        fun newInstance(pageType: String): PageSortDialog {
            val args = Bundle()
            args.putString(PageSort.PAGE_TYPE, pageType)
            val fragment = PageSortDialog()
            fragment.arguments = args
            return fragment
        }

        /**
         * Shows the page sort dialog for the given [pageType].
         *
         * @param pageType One of the [PageSort].PAGE_TYPE_* constants.
         * @return The shown [PageSortDialog].
         */
        fun FragmentManager.showPageSortDialog(pageType: String): PageSortDialog {
            val dialog = newInstance(pageType)
            dialog.show(this, TAG)
            return dialog
        }
    }
}

