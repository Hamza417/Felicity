package app.simple.felicity.dialogs.songs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.decorations.toggles.FelicityChipGroup
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.shared.utils.BarHeight

class SongsMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.listStyleChipGroup.setChips(
                if (BarHeight.isLandscape(requireContext())) {
                    listOf(
                            FelicityChipGroup.ChipButton(R.string.list),
                            FelicityChipGroup.ChipButton(R.string.list_x2),
                            FelicityChipGroup.ChipButton(R.string.List_x3),
                            FelicityChipGroup.ChipButton(R.string.grid_x2),
                            FelicityChipGroup.ChipButton(R.string.grid_x3),
                            FelicityChipGroup.ChipButton(R.string.grid_x4),
                            FelicityChipGroup.ChipButton(R.string.grid_x5),
                            FelicityChipGroup.ChipButton(R.string.grid_x6)
                    )
                } else {
                    listOf(
                            FelicityChipGroup.ChipButton(R.string.list),
                            FelicityChipGroup.ChipButton(R.string.list_x2),
                            FelicityChipGroup.ChipButton(R.string.grid_x2),
                            FelicityChipGroup.ChipButton(R.string.grid_x3),
                            FelicityChipGroup.ChipButton(R.string.grid_x4),
                    )
                }
        )

        binding.listStyleChipGroup.setSelectedIndex(
                when (SongsPreferences.getGridSize()) {
                    CommonPreferencesConstants.LayoutMode.LIST_ONE -> 0
                    CommonPreferencesConstants.LayoutMode.LIST_TWO -> 1
                    CommonPreferencesConstants.LayoutMode.LIST_THREE -> if (BarHeight.isLandscape(requireContext())) 2 else 2
                    CommonPreferencesConstants.LayoutMode.GRID_TWO -> if (BarHeight.isLandscape(requireContext())) 3 else 2
                    CommonPreferencesConstants.LayoutMode.GRID_THREE -> if (BarHeight.isLandscape(requireContext())) 4 else 3
                    CommonPreferencesConstants.LayoutMode.GRID_FOUR -> if (BarHeight.isLandscape(requireContext())) 5 else 4
                    CommonPreferencesConstants.LayoutMode.GRID_FIVE -> if (BarHeight.isLandscape(requireContext())) 6 else 0
                    CommonPreferencesConstants.LayoutMode.GRID_SIX -> if (BarHeight.isLandscape(requireContext())) 7 else 0
                }
        )

        binding.listStyleChipGroup.setOnSelectionChangedListener { ints ->
            if (BarHeight.isLandscape(requireContext())) {
                when (ints.firstOrNull()) {
                    0 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.LIST_ONE)
                    1 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.LIST_TWO)
                    2 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.LIST_THREE)
                    3 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_TWO)
                    4 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_THREE)
                    5 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_FOUR)
                    6 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_FIVE)
                    7 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_SIX)
                }
            } else {
                when (ints.firstOrNull()) {
                    0 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.LIST_ONE)
                    1 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.LIST_TWO)
                    2 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_TWO)
                    3 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_THREE)
                    4 -> SongsPreferences.setGridSize(CommonPreferencesConstants.LayoutMode.GRID_FOUR)
                }
            }

            binding.openAppSettings.setOnClickListener {
                openAppSettings()
            }
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