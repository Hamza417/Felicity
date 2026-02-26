package app.simple.felicity.dialogs.folders

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortFolderHierarchyBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.FolderHierarchyPreferences

class DialogFolderHierarchySort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortFolderHierarchyBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSortFolderHierarchyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (FolderHierarchyPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> binding.name.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
            CommonPreferencesConstants.BY_PATH -> binding.path.isChecked = true
        }

        binding.normal.isChecked = FolderHierarchyPreferences.getSortOrder() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = FolderHierarchyPreferences.getSortOrder() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.name.id -> FolderHierarchyPreferences.setSortStyle(CommonPreferencesConstants.BY_NAME)
                binding.numberOfSongs.id -> FolderHierarchyPreferences.setSortStyle(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
                binding.path.id -> FolderHierarchyPreferences.setSortStyle(CommonPreferencesConstants.BY_PATH)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> FolderHierarchyPreferences.setSortOrder(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> FolderHierarchyPreferences.setSortOrder(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "FolderHierarchySort"

        fun newInstance(): DialogFolderHierarchySort {
            val args = Bundle()
            val fragment = DialogFolderHierarchySort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showFolderHierarchySortDialog(): DialogFolderHierarchySort {
            val fragment = findFragmentByTag(TAG) as? DialogFolderHierarchySort ?: newInstance()
            if (!fragment.isAdded) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}

