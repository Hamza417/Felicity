package app.simple.felicity.dialogs.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortSearchBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.SearchPreferences

class SearchSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortSearchBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (SearchPreferences.getSongSort()) {
            CommonPreferencesConstants.BY_TITLE -> binding.title.isChecked = true
            CommonPreferencesConstants.BY_ARTIST -> binding.artist.isChecked = true
            CommonPreferencesConstants.BY_ALBUM -> binding.album.isChecked = true
            CommonPreferencesConstants.BY_PATH -> binding.path.isChecked = true
            CommonPreferencesConstants.BY_DATE_ADDED -> binding.dateAdded.isChecked = true
            CommonPreferencesConstants.BY_DATE_MODIFIED -> binding.dateModified.isChecked = true
            CommonPreferencesConstants.BY_DURATION -> binding.duration.isChecked = true
            CommonPreferencesConstants.BY_YEAR -> binding.year.isChecked = true
            CommonPreferencesConstants.BY_TRACK_NUMBER -> binding.trackNumber.isChecked = true
            CommonPreferencesConstants.BY_COMPOSER -> binding.composer.isChecked = true
        }

        binding.normal.isChecked = SearchPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = SearchPreferences.getSortingStyle() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.title.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_TITLE)
                binding.artist.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_ARTIST)
                binding.album.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_ALBUM)
                binding.path.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_PATH)
                binding.dateAdded.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_DATE_ADDED)
                binding.dateModified.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_DATE_MODIFIED)
                binding.duration.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_DURATION)
                binding.year.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_YEAR)
                binding.trackNumber.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_TRACK_NUMBER)
                binding.composer.id -> SearchPreferences.setSongSort(CommonPreferencesConstants.BY_COMPOSER)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> SearchPreferences.setSortingStyle(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> SearchPreferences.setSortingStyle(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        fun newInstance(): SearchSort {
            val args = Bundle()
            val fragment = SearchSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showSearchSort(): SearchSort {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "SearchSort"
    }
}

