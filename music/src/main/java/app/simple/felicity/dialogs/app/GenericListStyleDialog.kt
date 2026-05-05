package app.simple.felicity.dialogs.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogListStyleBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.shared.constants.BundleConstants
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.shared.utils.ViewUtils.visible

class GenericListStyleDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogListStyleBinding
    private lateinit var panel: PANEL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogListStyleBinding.inflate(inflater, container, false)

        panel = PANEL.valueOf(requireArguments().getString(BundleConstants.PANEL, PANEL.SONGS.name)!!)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (isLandscape) {
            binding.fiveX.visible()
            binding.sixX.visible()
        } else {
            binding.fiveX.gone()
            binding.sixX.gone()
        }

        highlightSelected(getLayoutModeForPanel())

        binding.relaxed.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.LABEL_ONE)
            dismiss()
        }

        binding.compact.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.LABEL_TWO)
            dismiss()
        }

        binding.single.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.LIST_ONE)
            dismiss()
        }

        binding.listDouble.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.LIST_TWO)
            dismiss()
        }

        binding.triple.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.LIST_THREE)
            dismiss()
        }

        binding.twoX.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.GRID_TWO)
            dismiss()
        }

        binding.threeX.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.GRID_THREE)
            dismiss()
        }

        binding.fourX.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.GRID_FOUR)
            dismiss()
        }

        binding.fiveX.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.GRID_FIVE)
            dismiss()
        }

        binding.sixX.setOnClickListener {
            setLayoutModeForPanel(CommonPreferencesConstants.LayoutMode.GRID_SIX)
            dismiss()
        }
    }

    private fun highlightSelected(layoutMode: CommonPreferencesConstants.LayoutMode) {
        binding.relaxed.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.LABEL_ONE)
        binding.compact.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.LABEL_TWO)
        binding.single.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.LIST_ONE)
        binding.listDouble.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.LIST_TWO)
        binding.triple.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.LIST_THREE)
        binding.twoX.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.GRID_TWO)
        binding.threeX.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.GRID_THREE)
        binding.fourX.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.GRID_FOUR)
        binding.fiveX.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.GRID_FIVE)
        binding.sixX.setUseAccentColor(layoutMode == CommonPreferencesConstants.LayoutMode.GRID_SIX)
    }

    private fun getLayoutModeForPanel(): CommonPreferencesConstants.LayoutMode {
        return when (panel) {
            PANEL.SONGS -> SongsPreferences.getGridSize()
            PANEL.ALBUMS -> AlbumPreferences.getGridSize()
            PANEL.ARTISTS -> ArtistPreferences.getGridSize()
            else -> {
                CommonPreferencesConstants.LayoutMode.LIST_ONE
            }
        }
    }

    private fun setLayoutModeForPanel(layoutMode: CommonPreferencesConstants.LayoutMode) {
        when (panel) {
            PANEL.SONGS -> SongsPreferences.setGridSize(layoutMode)
            PANEL.ALBUMS -> AlbumPreferences.setGridSize(layoutMode)
            PANEL.ARTISTS -> ArtistPreferences.setGridSize(layoutMode)
            else -> {
                // Do nothing since the other panels don't have layout modes.
            }
        }
    }

    companion object {
        private const val TAG = "GenericListStyleDialog"

        fun newInstance(panel: PANEL): GenericListStyleDialog {
            val args = Bundle()
            args.putString(BundleConstants.PANEL, panel.name)
            val fragment = GenericListStyleDialog()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showListStyleDialog(panel: PANEL): GenericListStyleDialog {
            val dialog = newInstance(panel)
            dialog.show(this, TAG)
            return dialog
        }

        enum class PANEL(val title: String) {
            SONGS("songs"),
            ALBUMS("albums"),
            ARTISTS("artists"),
            PLAYLISTS("playlists"),
            GENRES("genres"),
            FOLDERS("folders")
        }
    }
}
