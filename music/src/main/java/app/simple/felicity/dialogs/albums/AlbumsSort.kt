package app.simple.felicity.dialogs.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSortAlbumsBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.preferences.SongsPreferences

class AlbumsSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortAlbumsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (AlbumPreferences.getAlbumSort()) {
            AlbumPreferences.BY_ALBUM_NAME -> binding.title.isChecked = true
            AlbumPreferences.BY_ARTIST -> binding.artist.isChecked = true
            AlbumPreferences.BY_FIRST_YEAR -> binding.firstYear.isChecked = true
            AlbumPreferences.BY_LAST_YEAR -> binding.lastYear.isChecked = true
            AlbumPreferences.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
        }

        binding.normal.isChecked = AlbumPreferences.getSortingStyle() == AlbumPreferences.ACCENDING
        binding.reversed.isChecked = AlbumPreferences.getSortingStyle() == AlbumPreferences.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.title.id -> AlbumPreferences.setAlbumSort(AlbumPreferences.BY_ALBUM_NAME)
                binding.artist.id -> AlbumPreferences.setAlbumSort(AlbumPreferences.BY_ARTIST)
                binding.numberOfSongs.id -> AlbumPreferences.setAlbumSort(AlbumPreferences.BY_NUMBER_OF_SONGS)
                binding.firstYear.id -> AlbumPreferences.setAlbumSort(AlbumPreferences.BY_FIRST_YEAR)
                binding.lastYear.id -> AlbumPreferences.setAlbumSort(AlbumPreferences.BY_LAST_YEAR)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> AlbumPreferences.setSortingStyle(SongsPreferences.ACCENDING)
                binding.reversed.id -> AlbumPreferences.setSortingStyle(SongsPreferences.DESCENDING)
            }
        }
    }

    companion object {
        fun newInstance(): AlbumsSort {
            val args = Bundle()
            val fragment = AlbumsSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showAlbumsSort(): AlbumsSort {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "AlbumsSort"
    }
}