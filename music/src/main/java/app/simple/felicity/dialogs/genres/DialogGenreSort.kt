package app.simple.felicity.dialogs.genres

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSortGenresBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.GenresPreferences

class DialogGenreSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortGenresBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (GenresPreferences.getSortStyle()) {
            GenresPreferences.BY_NAME -> binding.name.isChecked = true
        }

        binding.normal.isChecked = GenresPreferences.getSortOrder() == GenresPreferences.ACCENDING
        binding.reversed.isChecked = GenresPreferences.getSortOrder() == GenresPreferences.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> GenresPreferences.setSortStyle(GenresPreferences.BY_NAME)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> GenresPreferences.setSortOrder(GenresPreferences.ACCENDING)
                binding.reversed.id -> GenresPreferences.setSortOrder(GenresPreferences.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "GenresSort"

        fun newInstance(): DialogGenreSort {
            val args = Bundle()
            val fragment = DialogGenreSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showGenresSortDialog(): DialogGenreSort {
            val fragment = findFragmentByTag(TAG) as? DialogGenreSort ?: newInstance()
            if (!fragment.isAdded) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}