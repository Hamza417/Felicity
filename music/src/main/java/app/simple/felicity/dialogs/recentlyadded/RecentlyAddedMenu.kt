package app.simple.felicity.dialogs.recentlyadded

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.RecentlyAddedPreferences

/**
 * Bottom-sheet menu dialog for the Recently Added panel containing the list style selector.
 *
 * @author Hamza417
 */
class RecentlyAddedMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isLandscape = AppOrientation.isLandscape()
        val list: List<CommonPreferencesConstants.LayoutMode> = if (isLandscape) {
            listOf(
                    CommonPreferencesConstants.LayoutMode.LIST_ONE,
                    CommonPreferencesConstants.LayoutMode.LIST_TWO,
                    CommonPreferencesConstants.LayoutMode.LIST_THREE,
                    CommonPreferencesConstants.LayoutMode.GRID_TWO,
                    CommonPreferencesConstants.LayoutMode.GRID_THREE,
                    CommonPreferencesConstants.LayoutMode.GRID_FOUR,
                    CommonPreferencesConstants.LayoutMode.GRID_FIVE,
                    CommonPreferencesConstants.LayoutMode.GRID_SIX,
            )
        } else {
            listOf(
                    CommonPreferencesConstants.LayoutMode.LIST_ONE,
                    CommonPreferencesConstants.LayoutMode.LIST_TWO,
                    CommonPreferencesConstants.LayoutMode.GRID_TWO,
                    CommonPreferencesConstants.LayoutMode.GRID_THREE,
                    CommonPreferencesConstants.LayoutMode.GRID_FOUR,
            )
        }

        binding.listStyleSeekbar.setStepMode(true)
        binding.listStyleSeekbar.setMin(1f)
        binding.listStyleSeekbar.setMax(list.size.toFloat())
        binding.listStyleSeekbar.setStepSize(1)
        binding.listStyleSeekbar.setStep(list.indexOf(RecentlyAddedPreferences.getGridSize()))

        binding.listStyleSeekbar.setOnStepSeekChangeListener(object : FelicitySeekbar.OnStepSeekChangeListener {
            override fun onStepChanged(seekbar: FelicitySeekbar, step: Int, fromUser: Boolean) {
            }

            override fun onStopTrackingTouch(seekbar: FelicitySeekbar) {
                val mode = list.getOrNull(seekbar.getCurrentStep()) ?: return
                RecentlyAddedPreferences.setGridSize(mode)
            }
        })

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }

    companion object {
        private const val TAG = "RecentlyAddedMenu"

        fun newInstance(): RecentlyAddedMenu {
            val args = Bundle()
            val fragment = RecentlyAddedMenu()
            fragment.arguments = args
            return fragment
        }

        /**
         * Shows a [RecentlyAddedMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showRecentlyAddedMenu(): RecentlyAddedMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

