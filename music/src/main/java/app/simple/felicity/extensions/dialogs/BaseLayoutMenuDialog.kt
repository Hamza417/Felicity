package app.simple.felicity.extensions.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import app.simple.felicity.constants.CommonPreferencesConstants.LayoutMode
import app.simple.felicity.core.singletons.AppOrientation
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.decorations.highlight.ListStyleIconView
import app.simple.felicity.shared.utils.UnitUtils.dpToPx

/**
 * Abstract base class for all panel layout-style menu bottom-sheet dialogs.
 *
 * Owns the icon-strip layout-mode selector and the "Open app settings" button,
 * eliminating duplicated boilerplate found in every individual panel menu dialog.
 * Subclasses only need to implement [getLayoutMode] and [setLayoutMode] to wire the
 * icons to their respective panel preference objects.
 *
 * @author Hamza417
 */
abstract class BaseLayoutMenuDialog : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    /**
     * Keeps track of all the icon views so we can flip the highlighted state when the
     * user taps a different mode without having to scan the container children.
     */
    private val iconViews = mutableListOf<ListStyleIconView>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val list = buildLayoutModes(AppOrientation.isLandscape())
        val current = getLayoutMode()

        populateIconStrip(list, current)

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    /**
     * Builds one [ListStyleIconView] per available [LayoutMode] and adds them all to the
     * horizontal container. The icon that matches the current mode starts highlighted.
     */
    private fun populateIconStrip(modes: List<LayoutMode>, current: LayoutMode) {
        iconViews.clear()
        binding.listStyleIconsContainer.removeAllViews()

        val iconSizePx = requireContext().dpToPx(48f).toInt()
        val iconGapPx = requireContext().dpToPx(8f).toInt()

        modes.forEach { mode ->
            val icon = ListStyleIconView(requireContext()).apply {
                layoutMode = mode
                isHighlighted = (mode == current)

                val params = LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                    marginEnd = iconGapPx
                }
                layoutParams = params

                setOnClickListener {
                    if (layoutMode != getLayoutMode()) {
                        setLayoutMode(layoutMode)
                        // Move the highlight to the tapped icon and clear the rest.
                        iconViews.forEach { it.isHighlighted = (it === this) }
                    }
                }
            }

            iconViews.add(icon)
            binding.listStyleIconsContainer.addView(icon)
        }
    }

    /**
     * Returns the current [LayoutMode] that is persisted for this panel.
     */
    abstract fun getLayoutMode(): LayoutMode

    /**
     * Persists the given [LayoutMode] for this panel.
     *
     * @param mode the newly selected layout mode to save.
     */
    abstract fun setLayoutMode(mode: LayoutMode)

    /**
     * Builds the ordered list of available [LayoutMode] values for the given orientation.
     *
     * Override in a subclass to restrict or extend the default set, for example when the
     * panel's adapter does not support label-style view types.
     *
     * @param isLandscape true if the device is currently in landscape orientation.
     * @return the ordered list of layout modes to display in the icon strip.
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
