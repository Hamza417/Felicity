package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterFolders
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentFoldersBinding
import app.simple.felicity.databinding.HeaderFoldersBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.folders.DialogFolderSort.Companion.showFoldersSortDialog
import app.simple.felicity.dialogs.folders.FoldersMenu.Companion.showFoldersMenu
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.FoldersPreferences
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.sort.FolderSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.FolderSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.FolderPage
import app.simple.felicity.viewmodels.panels.FoldersViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panel fragment displaying the user's music folders with sort and grid layout support.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Folders : PanelFragment() {

    private val foldersViewModel: FoldersViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentFoldersBinding
    private lateinit var headerBinding: HeaderFoldersBinding

    private var gridLayoutManager: GridLayoutManager? = null
    private var adapterFolders: AdapterFolders? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFoldersBinding.inflate(inflater, container, false)
        headerBinding = HeaderFoldersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "onViewCreated: adapterFolders=${adapterFolders != null}")

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        val mode = FoldersPreferences.getGridSize()
        gridLayoutManager = GridLayoutManager(requireContext(), mode.spanCount)
        binding.recyclerView.layoutManager = gridLayoutManager

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                foldersViewModel.folders
                    .collect { folders ->
                        if (folders.isNotEmpty()) {
                            updateFoldersList(folders)
                        } else if (adapterFolders != null) {
                            updateFoldersList(folders)
                        }
                    }
            }
        }
    }

    override fun onDestroyView() {
        Log.d(TAG, "onDestroyView: Clearing adapter reference")
        adapterFolders = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.menu.setOnClickListener {
            childFragmentManager.showFoldersMenu()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showFoldersSortDialog()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showFoldersSortDialog()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }
    }

    private fun updateFoldersList(folders: List<Folder>) {
        Log.d(TAG, "updateFoldersList: folders.size=${folders.size}, adapterFolders=${adapterFolders != null}, recyclerView.adapter=${binding.recyclerView.adapter != null}")

        if (adapterFolders == null) {
            Log.d(TAG, "updateFoldersList: Creating new adapter with ${folders.size} folders")
            adapterFolders = AdapterFolders(folders.toMutableList())
            adapterFolders?.setHasStableIds(true)
            adapterFolders?.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onFolderClicked(folder: Folder, view: View) {
                    Log.d(TAG, "onFolderClicked: Folder: ${folder.name}")
                    openFragment(FolderPage.newInstance(folder), FolderPage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterFolders
            Log.d(TAG, "updateFoldersList: Adapter attached to RecyclerView")
        } else {
            Log.d(TAG, "updateFoldersList: Updating existing adapter with ${folders.size} folders")
            adapterFolders?.updateList(folders)

            if (binding.recyclerView.adapter == null) {
                Log.d(TAG, "updateFoldersList: Re-attaching adapter to RecyclerView")
                binding.recyclerView.adapter = adapterFolders
            }
        }

        headerBinding.count.text = getString(R.string.x_folders, folders.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(folders),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(
                        CommonPreferencesConstants.BY_NAME,
                        CommonPreferencesConstants.BY_PATH
                ),
                preference = FoldersPreferences.getSortStyle()
        )
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            FoldersPreferences.GRID_SIZE_PORTRAIT, FoldersPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = FoldersPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(folders: List<Folder>): List<SectionedFastScroller.Position> {
        return when (FoldersPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                folders.forEachIndexed { index, folder ->
                    val firstChar = folder.name.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
            CommonPreferencesConstants.BY_PATH -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                folders.forEachIndexed { index, folder ->
                    val firstChar = folder.path.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "/"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
            else -> emptyList()
        }
    }

    companion object {
        const val TAG = "FoldersFragment"

        fun newInstance(): Folders {
            val args = Bundle()
            val fragment = Folders()
            fragment.arguments = args
            return fragment
        }
    }
}
