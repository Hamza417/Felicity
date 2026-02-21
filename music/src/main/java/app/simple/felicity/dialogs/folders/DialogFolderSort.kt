package app.simple.felicity.dialogs.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortFoldersBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.FoldersPreferences

class DialogFolderSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortFoldersBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (FoldersPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> binding.name.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
            CommonPreferencesConstants.BY_PATH -> binding.path.isChecked = true
        }

        binding.normal.isChecked = FoldersPreferences.getSortOrder() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = FoldersPreferences.getSortOrder() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> FoldersPreferences.setSortStyle(CommonPreferencesConstants.BY_NAME)
                binding.numberOfSongs.id -> FoldersPreferences.setSortStyle(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
                binding.path.id -> FoldersPreferences.setSortStyle(CommonPreferencesConstants.BY_PATH)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> FoldersPreferences.setSortOrder(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> FoldersPreferences.setSortOrder(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "FoldersSort"

        fun newInstance(): DialogFolderSort {
            val args = Bundle()
            val fragment = DialogFolderSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showFoldersSortDialog(): DialogFolderSort {
            val fragment = findFragmentByTag(TAG) as? DialogFolderSort ?: newInstance()
            if (!fragment.isAdded) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}

