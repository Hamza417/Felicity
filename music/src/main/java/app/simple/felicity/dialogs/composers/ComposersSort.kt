package app.simple.felicity.dialogs.composers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortComposersBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.ComposerPreferences

class ComposersSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortComposersBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSortComposersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (ComposerPreferences.getComposerSort()) {
            CommonPreferencesConstants.BY_NAME -> binding.name.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
        }

        binding.normal.isChecked = ComposerPreferences.getSortingStyle() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = ComposerPreferences.getSortingStyle() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> ComposerPreferences.setComposerSort(CommonPreferencesConstants.BY_NAME)
                binding.numberOfSongs.id -> ComposerPreferences.setComposerSort(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> ComposerPreferences.setSortingStyle(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> ComposerPreferences.setSortingStyle(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "ComposersSort"

        fun newInstance(): ComposersSort {
            val args = Bundle()
            val fragment = ComposersSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showComposersSort(): ComposersSort {
            val fragment = findFragmentByTag(TAG) as? ComposersSort ?: newInstance()
            if (fragment.isAdded.not()) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}

