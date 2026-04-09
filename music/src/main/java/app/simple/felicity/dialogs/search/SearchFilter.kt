package app.simple.felicity.dialogs.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSearchFilterBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.SearchPreferences

/**
 * Bottom-sheet dialog that lets the user toggle which result categories
 * (Songs, Albums, Artists, Genres) are included in the Search panel output.
 * Preference changes are persisted immediately and picked up by [SearchViewModel]
 * through the shared-preference change listener.
 *
 * @author Hamza417
 */
class SearchFilter : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSearchFilterBinding

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View {
        binding = DialogSearchFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initChipStates()
        attachChipListeners()
    }

    private fun initChipStates() {
        binding.songs.isChecked = SearchPreferences.isSongsEnabled()
        binding.albums.isChecked = SearchPreferences.isAlbumsEnabled()
        binding.artists.isChecked = SearchPreferences.isArtistsEnabled()
        binding.genres.isChecked = SearchPreferences.isGenresEnabled()
    }

    private fun attachChipListeners() {
        binding.songs.setOnCheckedChangeListener { _, isChecked ->
            SearchPreferences.setSongsEnabled(isChecked)
        }
        binding.albums.setOnCheckedChangeListener { _, isChecked ->
            SearchPreferences.setAlbumsEnabled(isChecked)
        }
        binding.artists.setOnCheckedChangeListener { _, isChecked ->
            SearchPreferences.setArtistsEnabled(isChecked)
        }
        binding.genres.setOnCheckedChangeListener { _, isChecked ->
            SearchPreferences.setGenresEnabled(isChecked)
        }
    }

    companion object {
        private const val TAG = "SearchFilter"

        fun newInstance(): SearchFilter {
            return SearchFilter().apply { arguments = Bundle() }
        }

        /**
         * Creates and displays a new [SearchFilter] dialog using [childFragmentManager].
         */
        fun FragmentManager.showSearchFilter(): SearchFilter {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

