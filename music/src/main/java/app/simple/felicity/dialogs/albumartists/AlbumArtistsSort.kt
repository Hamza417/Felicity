package app.simple.felicity.dialogs.albumartists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortAlbumArtistsBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.AlbumArtistPreferences

/**
 * Bottom-sheet sort dialog for the Album Artists panel. Lets the user pick a sort field
 * (name, number of albums, number of songs) and an order (normal or reversed).
 * Each change is saved immediately so closing the sheet applies it right away.
 *
 * @author Hamza417
 */
class AlbumArtistsSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortAlbumArtistsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSortAlbumArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restore the current sort field selection so the user sees what they already picked
        when (AlbumArtistPreferences.getAlbumArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> binding.name.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS -> binding.numberOfAlbums.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
        }

        // Restore the current sort direction
        binding.normal.isChecked = AlbumArtistPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = AlbumArtistPreferences.getSortingStyle() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> AlbumArtistPreferences.setAlbumArtistSort(CommonPreferencesConstants.BY_NAME)
                binding.numberOfAlbums.id -> AlbumArtistPreferences.setAlbumArtistSort(CommonPreferencesConstants.BY_NUMBER_OF_ALBUMS)
                binding.numberOfSongs.id -> AlbumArtistPreferences.setAlbumArtistSort(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> AlbumArtistPreferences.setSortingStyle(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> AlbumArtistPreferences.setSortingStyle(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "AlbumArtistsSort"

        fun newInstance(): AlbumArtistsSort {
            val args = Bundle()
            val fragment = AlbumArtistsSort()
            fragment.arguments = args
            return fragment
        }

        /**
         * Shows the sort dialog, making sure we don't stack two of them on top of each other.
         */
        fun FragmentManager.showAlbumArtistsSort(): AlbumArtistsSort {
            val fragment = findFragmentByTag(TAG) as? AlbumArtistsSort ?: newInstance()
            if (fragment.isAdded.not()) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}

