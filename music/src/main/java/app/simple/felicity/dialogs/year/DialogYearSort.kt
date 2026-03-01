package app.simple.felicity.dialogs.year

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSortYearBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.YearPreferences

class DialogYearSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortYearBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSortYearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (YearPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_YEAR -> binding.year.isChecked = true
            CommonPreferencesConstants.BY_NUMBER_OF_SONGS -> binding.numberOfSongs.isChecked = true
        }

        binding.normal.isChecked = YearPreferences.getSortOrder() == CommonPreferencesConstants.ASCENDING
        binding.reversed.isChecked = YearPreferences.getSortOrder() == CommonPreferencesConstants.DESCENDING

        binding.sortByChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.year.id -> YearPreferences.setSortStyle(CommonPreferencesConstants.BY_YEAR)
                binding.numberOfSongs.id -> YearPreferences.setSortStyle(CommonPreferencesConstants.BY_NUMBER_OF_SONGS)
            }
        }

        binding.sortingStyleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                binding.normal.id -> YearPreferences.setSortOrder(CommonPreferencesConstants.ASCENDING)
                binding.reversed.id -> YearPreferences.setSortOrder(CommonPreferencesConstants.DESCENDING)
            }
        }
    }

    companion object {
        const val TAG = "YearSort"

        fun newInstance(): DialogYearSort {
            val args = Bundle()
            val fragment = DialogYearSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showYearSortDialog(): DialogYearSort {
            val fragment = findFragmentByTag(TAG) as? DialogYearSort ?: newInstance()
            if (!fragment.isAdded) {
                fragment.show(this, TAG)
            }
            return fragment
        }
    }
}

