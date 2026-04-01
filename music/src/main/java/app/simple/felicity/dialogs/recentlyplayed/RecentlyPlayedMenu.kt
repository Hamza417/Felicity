package app.simple.felicity.dialogs.recentlyplayed

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.decorations.toggles.FelicityChipGroup
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.RecentlyPlayedPreferences

/**
 * Bottom-sheet menu dialog for the Recently Played panel containing the list style selector.
 *
 * @author Hamza417
 */
class RecentlyPlayedMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isLandscape = AppOrientation.isLandscape()

        binding.listStyleChipGroup.setChips(
                if (isLandscape) {
                    listOf(
                            FelicityChipGroup.ChipButton(R.string.list, CommonPreferencesConstants.LayoutMode.LIST_ONE),
                            FelicityChipGroup.ChipButton(R.string.list_x2, CommonPreferencesConstants.LayoutMode.LIST_TWO),
                            FelicityChipGroup.ChipButton(R.string.List_x3, CommonPreferencesConstants.LayoutMode.LIST_THREE),
                            FelicityChipGroup.ChipButton(R.string.grid_x2, CommonPreferencesConstants.LayoutMode.GRID_TWO),
                            FelicityChipGroup.ChipButton(R.string.grid_x3, CommonPreferencesConstants.LayoutMode.GRID_THREE),
                            FelicityChipGroup.ChipButton(R.string.grid_x4, CommonPreferencesConstants.LayoutMode.GRID_FOUR),
                            FelicityChipGroup.ChipButton(R.string.grid_x5, CommonPreferencesConstants.LayoutMode.GRID_FIVE),
                            FelicityChipGroup.ChipButton(R.string.grid_x6, CommonPreferencesConstants.LayoutMode.GRID_SIX),
                    )
                } else {
                    listOf(
                            FelicityChipGroup.ChipButton(R.string.list, CommonPreferencesConstants.LayoutMode.LIST_ONE),
                            FelicityChipGroup.ChipButton(R.string.list_x2, CommonPreferencesConstants.LayoutMode.LIST_TWO),
                            FelicityChipGroup.ChipButton(R.string.grid_x2, CommonPreferencesConstants.LayoutMode.GRID_TWO),
                            FelicityChipGroup.ChipButton(R.string.grid_x3, CommonPreferencesConstants.LayoutMode.GRID_THREE),
                            FelicityChipGroup.ChipButton(R.string.grid_x4, CommonPreferencesConstants.LayoutMode.GRID_FOUR),
                    )
                }
        )

        binding.listStyleChipGroup.shouldRestoreStates = false
        binding.listStyleChipGroup.setSelectedByTag(RecentlyPlayedPreferences.getGridSize())

        binding.listStyleChipGroup.setOnSelectionChangedListener { selected ->
            val mode = selected.firstOrNull()?.tag as? CommonPreferencesConstants.LayoutMode ?: return@setOnSelectionChangedListener
            RecentlyPlayedPreferences.setGridSize(mode)
        }

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }

    companion object {
        private const val TAG = "RecentlyPlayedMenu"

        fun newInstance(): RecentlyPlayedMenu {
            val args = Bundle()
            val fragment = RecentlyPlayedMenu()
            fragment.arguments = args
            return fragment
        }

        /**
         * Shows a [RecentlyPlayedMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showRecentlyPlayedMenu(): RecentlyPlayedMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

