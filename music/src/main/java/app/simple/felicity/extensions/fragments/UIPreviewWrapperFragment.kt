package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.simple.felicity.databinding.FragmentUiPreviewPageBinding
import app.simple.felicity.extensions.fragments.UIPreviewWrapperFragment.Companion.fragmentRegistry

/**
 * A lightweight wrapper fragment used inside the UI-selection ViewPager2.
 *
 * Each instance hosts an arbitrary inner [Fragment] inside an
 * [app.simple.felicity.decorations.views.AspectRatioPreviewCardView] that enforces a fixed
 * 9:21 portrait aspect ratio without any runtime layout calculations. Touch interception
 * is handled directly by the card view so no additional overlay is required.
 *
 * Each page carries its own [app.simple.felicity.decorations.toggles.CheckBox] and label
 * so that the selection indicator scrolls together with the preview. Selection only changes
 * when the user explicitly taps the checkbox; swiping between pages is purely for preview.
 *
 * Inner fragment factories are held in the static [fragmentRegistry] map and resolved via
 * an integer key stored in the fragment's [Bundle] arguments, because [Fragment] objects
 * cannot be placed directly into a [Bundle].
 *
 * @author Hamza417
 */
class UIPreviewWrapperFragment : Fragment() {

    /**
     * Callback interface implemented by the parent fragment to receive checkbox-driven
     * selection changes.
     */
    interface OnPageSelectionListener {
        /**
         * Invoked when the user explicitly taps the checkbox on this page.
         *
         * @param position 0-based index of the page whose checkbox was tapped.
         */
        fun onPageSelected(position: Int)
    }

    private var _binding: FragmentUiPreviewPageBinding? = null
    private val binding: FragmentUiPreviewPageBinding
        get() = _binding!!

    /** 0-based index of this page within the ViewPager2. */
    val pageIndex: Int
        get() = requireArguments().getInt(ARG_POSITION, -1)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUiPreviewPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /**
         * If the inner fragment already exists in the child manager (e.g., after a
         * configuration change where FragmentManager restores it from saved state),
         * skip the transaction entirely. Only look up the factory when a fresh
         * fragment is actually needed.
         */
        if (childFragmentManager.findFragmentByTag(TAG_INNER) == null) {
            val key = requireArguments().getInt(ARG_KEY, -1)
            val factory = fragmentRegistry[key]
            if (factory != null) {
                childFragmentManager.beginTransaction()
                    .replace(binding.fragmentContainer.id, factory(), TAG_INNER)
                    .commitNow()
            }
        }

        val nameResId = requireArguments().getInt(ARG_NAME_RES_ID, 0)
        val isSelected = requireArguments().getBoolean(ARG_INITIAL_SELECTED, false)

        binding.selectionName.setText(nameResId)
        binding.selectionCheckbox.setChecked(isSelected)

        /**
         * Override the default toggle-on-click behavior so the checkbox behaves like
         * a radio button: tapping a checked item does nothing; tapping an unchecked
         * item checks it with animation and notifies the parent.
         */
        binding.selectionCheckbox.setOnClickListener {
            if (!binding.selectionCheckbox.isChecked) {
                binding.selectionCheckbox.setChecked(true, true)
                (requireParentFragment() as? OnPageSelectionListener)?.onPageSelected(pageIndex)
            }
        }

        binding.selectionItemContainer.setOnClickListener {
            binding.selectionCheckbox.performClick()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Updates the checked state of this page's checkbox.
     *
     * Safe to call at any time; silently ignored if the view is not yet created.
     *
     * @param selected Whether this page should appear selected.
     * @param animate  Whether to animate the transition (default `true`).
     */
    fun setSelected(selected: Boolean, animate: Boolean = true) {
        _binding?.selectionCheckbox?.setChecked(selected, animate)
    }

    companion object {

        private const val ARG_KEY = "preview_fragment_key"
        private const val ARG_NAME_RES_ID = "preview_name_res_id"
        private const val ARG_POSITION = "preview_position"
        private const val ARG_INITIAL_SELECTED = "preview_initial_selected"
        private const val TAG_INNER = "inner_fragment"

        /** Holds [() -> Fragment] factories keyed by an auto-incrementing integer. */
        private val fragmentRegistry = HashMap<Int, () -> Fragment>()
        private var nextKey = 0

        /**
         * Creates a new [UIPreviewWrapperFragment] that will host a fragment produced by [factory].
         *
         * @param factory     Lambda that creates the inner fragment on demand.
         * @param nameResId   String resource ID for the label shown below the preview card.
         * @param position    0-based index of this page within the ViewPager2.
         * @param isSelected  Whether this page should start in the selected (checked) state.
         * @return A fully configured [UIPreviewWrapperFragment] ready to be used inside a ViewPager2 adapter.
         */
        fun newInstance(
                factory: () -> Fragment,
                nameResId: Int,
                position: Int,
                isSelected: Boolean
        ): UIPreviewWrapperFragment {
            val key = nextKey++
            fragmentRegistry[key] = factory
            return UIPreviewWrapperFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_KEY, key)
                    putInt(ARG_NAME_RES_ID, nameResId)
                    putInt(ARG_POSITION, position)
                    putBoolean(ARG_INITIAL_SELECTED, isSelected)
                }
            }
        }

        /**
         * Releases all cached factories. Call when the owning screen is fully destroyed
         * to avoid retaining factory references longer than necessary.
         */
        fun clearRegistry() {
            fragmentRegistry.clear()
            nextKey = 0
        }
    }
}
