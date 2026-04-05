package app.simple.felicity.extensions.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar

/**
 * Abstract base class for all panel layout-style menu bottom-sheet dialogs.
 *
 * Owns the common seekbar-based list-style selector and the "Open app settings" button,
 * eliminating the duplicated boilerplate found in every individual panel menu dialog.
 * Subclasses only need to implement [getLayoutMode] and [setLayoutMode] to wire the
 * seekbar to their respective panel preference objects.
 *
 * @author Hamza417
 */
abstract class BaseLayoutMenuDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = buildLayoutModes(AppOrientation.isLandscape())

        binding.listStyleSeekbar.setStepMode(true)
        binding.listStyleSeekbar.setMin(1f)
        binding.listStyleSeekbar.setMax(list.size.toFloat())
        binding.listStyleSeekbar.setStepSize(1)
        binding.listStyleSeekbar.setStep(list.indexOf(getLayoutMode()))

        binding.listStyleSeekbar.setOnStepSeekChangeListener(object : FelicitySeekbar.OnStepSeekChangeListener {
            override fun onStepChanged(seekbar: FelicitySeekbar, step: Int, fromUser: Boolean) {
            }

            override fun onStopTrackingTouch(seekbar: FelicitySeekbar) {
                val mode = list.getOrNull(seekbar.getCurrentStep()) ?: return
                setLayoutMode(mode)
            }
        })

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    /**
     * Returns the current [LayoutMode] that is persisted for this panel.
     */
    abstract fun getLayoutMode(): LayoutMode

    /**
     * Persists the given [LayoutMode] for this panel.
     *
     * @param mode the newly selected layout mode to save
     */
    abstract fun setLayoutMode(mode: LayoutMode)

    /**
     * Builds the ordered list of available [LayoutMode] values for the given orientation.
     *
     * Override in a subclass to restrict or extend the default set, for example when the
     * panel's adapter does not support label-style view types.
     *
     * @param isLandscape true if the device is currently in landscape orientation
     * @return the ordered list of layout modes to display on the seekbar
     */
    open fun buildLayoutModes(isLandscape: Boolean): List<LayoutMode> {
        return if (isLandscape) {
            listOf(
                    LayoutMode.LABEL_ONE,
                    LayoutMode.LABEL_TWO,
                    LayoutMode.LIST_ONE,
                    LayoutMode.LIST_TWO,
                    LayoutMode.LIST_THREE,
                    LayoutMode.GRID_TWO,
                    LayoutMode.GRID_THREE,
                    LayoutMode.GRID_FOUR,
                    LayoutMode.GRID_FIVE,
                    LayoutMode.GRID_SIX,
            )
        } else {
            listOf(
                    LayoutMode.LABEL_ONE,
                    LayoutMode.LABEL_TWO,
                    LayoutMode.LIST_ONE,
                    LayoutMode.LIST_TWO,
                    LayoutMode.GRID_TWO,
                    LayoutMode.GRID_THREE,
                    LayoutMode.GRID_FOUR,
            )
        }
    }
}

