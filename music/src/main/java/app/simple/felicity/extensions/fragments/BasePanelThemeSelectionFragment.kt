package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import app.simple.felicity.databinding.FragmentUiSelectionBinding

/**
 * Abstract reusable base fragment that presents a fixed-aspect-ratio ViewPager2 showing
 * live previews of UI variants. Each pager page carries its own checkbox and label so
 * that the selection indicator scrolls together with the preview.
 *
 * Selection only changes when the user explicitly taps a page's checkbox; swiping
 * between pages is purely for previewing without auto-committing a choice.
 *
 * Subclasses only need to:
 *  1. Provide the list of [UIEntry] objects via [getUIEntries].
 *  2. Return the currently active selection index via [getCurrentSelection].
 *  3. Persist the user's choice in [onSelectionChanged].
 *
 * @author Hamza417
 */
abstract class BasePanelThemeSelectionFragment :
        MediaFragment(),
        UIPreviewWrapperFragment.OnPageSelectionListener {

    /**
     * Describes a single UI variant entry for display in the selection pager.
     *
     * @param factory    Lambda that produces a fresh [Fragment] instance for this variant.
     * @param nameResId  String resource ID for the human-readable label shown on the page.
     */
    data class UIEntry(
            val factory: () -> Fragment,
            @StringRes val nameResId: Int
    )

    private var _binding: FragmentUiSelectionBinding? = null
    private val binding: FragmentUiSelectionBinding
        get() = _binding!!

    /** Provides the ordered list of [UIEntry] items to populate the pager. */
    abstract fun getUIEntries(): List<UIEntry>

    /**
     * Returns the index (0-based) of the currently active selection so the
     * fragment can initialize each page's checkbox in the correct state.
     */
    abstract fun getCurrentSelection(): Int

    /**
     * Called when the user explicitly taps a page's checkbox to commit a new selection.
     *
     * @param index 0-based index into the list returned by [getUIEntries].
     */
    abstract fun onSelectionChanged(index: Int)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUiSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()
        setupPager()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        UIPreviewWrapperFragment.clearRegistry()
        _binding = null
    }

    /**
     * Handles an explicit checkbox tap from a child [UIPreviewWrapperFragment].
     *
     * Iterates all currently active wrapper pages in the child fragment manager and
     * updates each checkbox to reflect the new selection, then notifies [onSelectionChanged].
     *
     * @param position 0-based index of the page that was selected.
     */
    override fun onPageSelected(position: Int) {
        childFragmentManager.fragments
            .filterIsInstance<UIPreviewWrapperFragment>()
            .forEach { page ->
                page.setSelected(page.pageIndex == position, animate = true)
            }
        onSelectionChanged(position)
    }

    private fun setupPager() {
        val entries = getUIEntries()
        binding.viewPager.adapter = UIPagerAdapter(entries)
        binding.viewPager.setCurrentItem(getCurrentSelection(), false)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    override fun getTransitionType(): TransitionType = TransitionType.SLIDE

    /**
     * Internal [FragmentStateAdapter] that creates a [UIPreviewWrapperFragment] for each
     * entry, forwarding the factory, label, position, and initial selection state.
     */
    private inner class UIPagerAdapter(
            private val entries: List<UIEntry>
    ) : FragmentStateAdapter(childFragmentManager, viewLifecycleOwner.lifecycle) {

        override fun getItemCount(): Int = entries.size

        override fun createFragment(position: Int): Fragment {
            val entry = entries[position]
            return UIPreviewWrapperFragment.newInstance(
                    factory = entry.factory,
                    nameResId = entry.nameResId,
                    position = position,
                    isSelected = (position == getCurrentSelection())
            )
        }
    }
}
