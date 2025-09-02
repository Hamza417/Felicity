package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortSongsBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.SongsPreferences

class SongsSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortSongsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (SongsPreferences.getSongSort()) {
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

        binding.normal.isChecked = SongsPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = SongsPreferences.getSortingStyle() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.title.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_TITLE)
                binding.artist.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_ARTIST)
                binding.album.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_ALBUM)
                binding.path.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_PATH)
                binding.dateAdded.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_DATE_ADDED)
                binding.dateModified.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_DATE_MODIFIED)
                binding.duration.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_DURATION)
                binding.year.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_YEAR)
                binding.trackNumber.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_TRACK_NUMBER)
                binding.composer.id -> SongsPreferences.setSongSort(CommonPreferencesConstants.BY_COMPOSER)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> SongsPreferences.setSortingStyle(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> SongsPreferences.setSortingStyle(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        fun newInstance(): SongsSort {
            val args = Bundle()
            val fragment = SongsSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showSongsSort(): SongsSort {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "SongsSort"
    }
}