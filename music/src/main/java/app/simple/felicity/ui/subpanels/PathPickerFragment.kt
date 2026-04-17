package app.simple.felicity.ui.subpanels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.ui.lists.AdapterPathPicker
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.ui.subpanels.PathPickerFragment.Companion.REQUEST_KEY_PATH_SELECTED
import app.simple.felicity.ui.subpanels.PathPickerFragment.Companion.RESULT_KEY_PATH
import app.simple.felicity.viewmodels.panels.PathPickerViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * A fragment that lets the user browse their device's storage and pick a folder or audio file path.
 *
 * Think of it like a mini file explorer, but cleaner — it only shows folders and audio files
 * that Felicity actually understands. Tap a folder to go inside it, long-press a folder to
 * select it as the result, or tap an audio file to select it directly.
 *
 * The header row at the top of the list shows the current directory path and a confirm (✓)
 * button that selects the current directory without having to long-press anything.
 *
 * The chosen path is delivered back to the calling fragment via the Fragment Result API
 * using [REQUEST_KEY_PATH_SELECTED]. The path string lives under [RESULT_KEY_PATH].
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class PathPickerFragment : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    private var adapter: AdapterPathPicker? = null
    private val viewModel: PathPickerViewModel by viewModels()

    /**
     * Intercepts the back gesture while we're inside a subdirectory.
     * Goes up one level instead of closing the whole fragment.
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.navigateBack()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireHiddenMiniPlayer()

        // Register back interception so we can go up a directory instead of closing.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 1)

        setupAdapter()
        observeViewModel()

        view.startTransitionOnPreDraw()
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    private fun setupAdapter() {
        adapter = AdapterPathPicker(
                onNavigate = { item ->
                    // The user tapped a folder — drill into it.
                    viewModel.navigateTo(item.file.absolutePath)
                },
                onSelect = { item ->
                    // The user picked a path — deliver it back and close.
                    deliverResult(item.file.absolutePath)
                },
                onConfirm = {
                    // The check button in the header confirms the current directory.
                    viewModel.currentPath.value?.let { deliverResult(it) }
                }
        )
        binding.recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            adapter?.submitList(items)
        }

        viewModel.currentPath.observe(viewLifecycleOwner) { path ->
            adapter?.updateCurrentPath(path)
            // Enable the back intercept only when we're inside a subdirectory.
            backCallback.isEnabled = !viewModel.isAtRoot()
        }
    }

    /**
     * Send the chosen path back to whoever opened us, then close the picker.
     * We disable the back intercept first so goBack() actually pops the fragment
     * rather than being swallowed by the directory-up handler.
     */
    private fun deliverResult(path: String) {
        backCallback.isEnabled = false
        parentFragmentManager.setFragmentResult(
                REQUEST_KEY_PATH_SELECTED,
                bundleOf(RESULT_KEY_PATH to path)
        )
        goBack()
    }

    override fun getTransitionType(): TransitionType = TransitionType.SLIDE

    override val wantsMiniPlayerVisible: Boolean get() = false

    companion object {
        fun newInstance(): PathPickerFragment = PathPickerFragment().apply { arguments = Bundle() }

        const val TAG = "PathPickerFragment"

        /** Fragment Result API key — listen for this in the calling fragment. */
        const val REQUEST_KEY_PATH_SELECTED = "path_picker_path_selected"

        /** The bundle key for the selected path string. */
        const val RESULT_KEY_PATH = "selected_path"
    }
}
