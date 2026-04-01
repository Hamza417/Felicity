package app.simple.felicity.dialogs.songs

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
import app.simple.felicity.preferences.SongsPreferences

class SongsMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
        binding.listStyleChipGroup.setSelectedByTag(SongsPreferences.getGridSize())

        binding.listStyleChipGroup.setOnSelectionChangedListener { selected ->
            val mode = selected.firstOrNull()?.tag as? CommonPreferencesConstants.LayoutMode ?: return@setOnSelectionChangedListener
            SongsPreferences.setGridSize(mode)
        }

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {

        }
    }

    companion object {
        fun newInstance(): SongsMenu {
            val args = Bundle()
            val fragment = SongsMenu()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "SongsMenu"

        fun FragmentManager.showSongsMenu(): SongsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}