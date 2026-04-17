package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterFolderList
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.ui.subpanels.PathPickerFragment

/**
 * Shows the list of folders that are always included in the library scan.
 *
 * The header row at the top of the list has a + button that opens [PathPickerFragment]
 * so the user can browse and pick a new folder to include. Each existing entry has a
 * remove button to kick it out of the list when it's no longer needed.
 *
 * @author Hamza417
 */
class WhitelistFolders : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding
    private var adapter: AdapterFolderList? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireHiddenMiniPlayer()

        setupAdapter()
        listenForPathResult()

        view.startTransitionOnPreDraw()
    }

    override fun onDestroyView() {
        adapter = null
        super.onDestroyView()
    }

    private fun setupAdapter() {
        adapter = AdapterFolderList(
                titleText = getString(R.string.included_folders),
                summaryText = getString(R.string.included_folders_summary),
                paths = LibraryPreferences.getIncludedFolders().toList().sorted(),
                onAdd = {
                    openFragment(PathPickerFragment.newInstance(), PathPickerFragment.TAG)
                },
                onRemove = { path ->
                    LibraryPreferences.removeIncludedFolder(path)
                    refreshList()
                }
        )
        binding.recyclerView.adapter = adapter
    }

    /**
     * Wait for [PathPickerFragment] to deliver a chosen path back to us,
     * then save it to the included folders list.
     */
    private fun listenForPathResult() {
        parentFragmentManager.setFragmentResultListener(
                PathPickerFragment.REQUEST_KEY_PATH_SELECTED,
                viewLifecycleOwner
        ) { _, bundle ->
            val path = bundle.getString(PathPickerFragment.RESULT_KEY_PATH) ?: return@setFragmentResultListener
            LibraryPreferences.addIncludedFolder(path)
            refreshList()
        }
    }

    private fun refreshList() {
        adapter?.submitList(LibraryPreferences.getIncludedFolders().toList().sorted())
    }

    override fun getTransitionType(): TransitionType = TransitionType.SLIDE

    override val wantsMiniPlayerVisible: Boolean get() = false

    companion object {
        fun newInstance(): WhitelistFolders = WhitelistFolders().apply { arguments = Bundle() }
        const val TAG = "WhitelistFolders"
    }
}
