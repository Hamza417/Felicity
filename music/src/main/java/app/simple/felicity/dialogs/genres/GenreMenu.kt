package app.simple.felicity.dialogs.genres

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogGenreMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.GenresPreferences

/**
 * Bottom-sheet menu dialog for the Genres panel, containing the list style selector
 * and the genre cover toggle.
 *
 * @author Hamza417
 */
class GenreMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogGenreMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogGenreMenuBinding.inflate(inflater, container, false)
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
        binding.listStyleSeekbar.setStep(list.indexOf(GenresPreferences.getGridSize()))

        binding.listStyleSeekbar.setOnStepSeekChangeListener(object : FelicitySeekbar.OnStepSeekChangeListener {
            override fun onStepChanged(seekbar: FelicitySeekbar, step: Int, fromUser: Boolean) {
            }

            override fun onStopTrackingTouch(seekbar: FelicitySeekbar) {
                val mode = list.getOrNull(seekbar.getCurrentStep()) ?: return
                GenresPreferences.setGridSize(mode)
            }
        })

        binding.genreCover.isChecked = GenresPreferences.isGenreCoversEnabled()

        binding.genreCover.setOnCheckedChangeListener { _, bool ->
            GenresPreferences.setGenreCoversEnabled(bool)
        }

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.SHOW_GENRE_COVERS -> {
                // Handled via preference listener in panel
            }
        }
    }

    companion object {
        fun newInstance(): GenreMenu {
            val args = Bundle()
            val fragment = GenreMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showGenreMenu(): GenreMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "GenreMenu"
    }
}