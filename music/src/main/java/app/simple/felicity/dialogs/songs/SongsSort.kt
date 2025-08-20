package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
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
            SongsPreferences.BY_TITLE -> binding.title.isChecked = true
            SongsPreferences.BY_ARTIST -> binding.artist.isChecked = true
            SongsPreferences.BY_ALBUM -> binding.album.isChecked = true
            SongsPreferences.PATH -> binding.path.isChecked = true
            SongsPreferences.BY_DATE_ADDED -> binding.dateAdded.isChecked = true
            SongsPreferences.BY_DATE_MODIFIED -> binding.dateModified.isChecked = true
            SongsPreferences.BY_DURATION -> binding.duration.isChecked = true
            SongsPreferences.BY_YEAR -> binding.year.isChecked = true
            SongsPreferences.BY_TRACK_NUMBER -> binding.trackNumber.isChecked = true
            SongsPreferences.BY_COMPOSER -> binding.composer.isChecked = true
            // TODOs
            // SongsPreferences.BY_DISC_NUMBER -> binding.discNumber.isChecked = true
            // SongsPreferences.BY_PLAY_COUNT -> binding.playCount.isChecked = true
            // SongsPreferences.BY_RATING -> binding.rating.isChecked = true
            // SongsPreferences.BY_FAVORITE -> binding.favorite.isChecked = true
        }

        binding.normal.isChecked = SongsPreferences.getSortingStyle() == SongsPreferences.ACCENDING
        binding.reversed.isChecked = SongsPreferences.getSortingStyle() == SongsPreferences.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.title.id -> SongsPreferences.setSongSort(SongsPreferences.BY_TITLE)
                binding.artist.id -> SongsPreferences.setSongSort(SongsPreferences.BY_ARTIST)
                binding.album.id -> SongsPreferences.setSongSort(SongsPreferences.BY_ALBUM)
                binding.path.id -> SongsPreferences.setSongSort(SongsPreferences.PATH)
                binding.dateAdded.id -> SongsPreferences.setSongSort(SongsPreferences.BY_DATE_ADDED)
                binding.dateModified.id -> SongsPreferences.setSongSort(SongsPreferences.BY_DATE_MODIFIED)
                binding.duration.id -> SongsPreferences.setSongSort(SongsPreferences.BY_DURATION)
                binding.year.id -> SongsPreferences.setSongSort(SongsPreferences.BY_YEAR)
                binding.trackNumber.id -> SongsPreferences.setSongSort(SongsPreferences.BY_TRACK_NUMBER)
                binding.composer.id -> SongsPreferences.setSongSort(SongsPreferences.BY_COMPOSER)
                // TODOs
                // binding.discNumber.id -> SongsPreferences.setSongSort(SongsPreferences.BY_DISC_NUMBER)
                // binding.playCount.id -> SongsPreferences.setSongSort(SongsPreferences.BY_PLAY_COUNT)
                // binding.rating.id -> SongsPreferences.setSongSort(SongsPreferences.BY_RATING)
                // binding.favorite.id -> SongsPreferences.setSongSort(SongsPreferences.BY_FAVORITE)
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