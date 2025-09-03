package app.simple.felicity.dialogs.songs

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortArtistsBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.ArtistPreferences

class ArtistsSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortArtistsBinding

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: android.os.Bundle?): android.view.View? {
        binding = DialogSortArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> binding.name.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> binding.numberOfAlbums.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
        }

        binding.normal.isChecked = ArtistPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = ArtistPreferences.getSortingStyle() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> ArtistPreferences.setArtistSort(CommonPreferencesConstants.BY_NAME)
                binding.numberOfAlbums.id -> ArtistPreferences.setArtistSort(CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS)
                binding.numberOfSongs.id -> ArtistPreferences.setArtistSort(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> ArtistPreferences.setSortingStyle(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> ArtistPreferences.setSortingStyle(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "ArtistsSort"

        fun newInstance(): ArtistsSort {
            val args = Bundle()
            val fragment = ArtistsSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showArtistsSort(): ArtistsSort {
            val fragment = findFragmentByTag(TAG) as? ArtistsSort ?: newInstance()
            if (fragment.isAdded.not()) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}