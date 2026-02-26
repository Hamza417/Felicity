package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterFolderHierarchy
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentFoldersHierarchyBinding
import app.simple.felicity.databinding.HeaderFoldersHierarchyBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.folders.DialogFolderHierarchySort.Companion.showFolderHierarchySortDialog
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.FolderHierarchyPreferences
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Folder
import app.simple.felicity.repository.sort.FolderHierarchySort.setCurrentSortOrder
import app.simple.felicity.repository.sort.FolderHierarchySort.setCurrentSortStyle
import app.simple.felicity.viewmodels.panels.FolderHierarchyViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FoldersHierarchy : PanelFragment() {

    /** Path passed via bundle, or null when this is the root level. */
    private val folderPath: String? by lazy {
        requireArguments().getString(KEY_FOLDER_PATH)
    }

    private val viewModel: FolderHierarchyViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<FolderHierarchyViewModel.Factory> {
                    it.create(folderPath = folderPath)
                }
            }
    )

    private lateinit var binding: FragmentFoldersHierarchyBinding
    private lateinit var headerBinding: HeaderFoldersHierarchyBinding

    private var gridLayoutManager: GridLayoutManager? = null
    private var adapter: AdapterFolderHierarchy? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFoldersHierarchyBinding.inflate(inflater, container, false)
        headerBinding = HeaderFoldersHierarchyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (folderPath == null) {
            headerBinding.headerTitle.text = getString(R.string.folders_hierarchy)
        } else {
            headerBinding.headerTitle.text = folderPath?.substringAfterLast('/')
        }

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        gridLayoutManager = GridLayoutManager(requireContext(), FolderHierarchyPreferences.getGridSize())
        binding.recyclerView.layoutManager = gridLayoutManager
        binding.recyclerView.setGridType(FolderHierarchyPreferences.getGridType(), FolderHierarchyPreferences.getGridSize())

        setupSpanSizeLookup()
        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.contents.collect { contents ->
                    updateContents(contents)
                }
            }
        }
    }

    override fun onDestroyView() {
        adapter = null
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupSpanSizeLookup() {
        gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                val viewType = adapter?.getItemViewType(position) ?: return 1
                // Folder rows always span the full width so they are never partial tiles
                return if (viewType in AdapterFolderHierarchy.FOLDER_VIEW_TYPES) {
                    gridLayoutManager?.spanCount ?: 1
                } else {
                    1
                }
            }
        }
    }

    private fun setupClickListeners() {
        headerBinding.menu.setOnClickListener {
            childFragmentManager.showFolderHierarchySortDialog()
        }
        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showFolderHierarchySortDialog()
        }
        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showFolderHierarchySortDialog()
        }
        headerBinding.search.setOnClickListener {
            openSearch()
        }

        headerBinding.gridSize.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(
                            R.string.one, R.string.two, R.string.three,
                            R.string.four, R.string.five, R.string.six
                    ),
                    menuIcons = listOf(
                            R.drawable.ic_one_16,
                            R.drawable.ic_two_16dp,
                            R.drawable.ic_three_16dp,
                            R.drawable.ic_four_16dp,
                            R.drawable.ic_five_16dp,
                            R.drawable.ic_six_16dp
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                            R.string.two -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                            R.string.three -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                            R.string.four -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                            R.string.five -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                            R.string.six -> FolderHierarchyPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                        }
                    },
                    onDismiss = {}
            ).show()
        }

        headerBinding.gridType.setOnClickListener { button ->
            SharedScrollViewPopup(
                    container = requireContainerView(),
                    anchorView = button,
                    menuItems = listOf(R.string.list, R.string.grid),
                    menuIcons = listOf(R.drawable.ic_list_16dp, R.drawable.ic_grid_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.list -> FolderHierarchyPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                            R.string.grid -> FolderHierarchyPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                        }
                    },
                    onDismiss = {}
            ).show()
        }
    }

    private fun updateContents(contents: FolderHierarchyViewModel.FolderHierarchyContents) {
        if (adapter == null) {
            adapter = AdapterFolderHierarchy(contents)
            adapter?.setCallbacks(object : GeneralAdapterCallbacks {
                override fun onFolderClicked(folder: Folder, view: View) {
                    // Open a new instance of this same fragment for the sub-folder.
                    // The back stack manages going back — no manual navigation stack needed.
                    openFragment(newInstance(folder.path), TAG)
                }

                override fun onSongClicked(songs: MutableList<Audio>, position: Int, view: View) {
                    setMediaItems(songs, position)
                }

                override fun onSongLongClicked(songs: List<Audio>, position: Int, view: View) {
                    openSongsMenu(songs, position, view as? android.widget.ImageView ?: return)
                }
            })
            binding.recyclerView.adapter = adapter
        } else {
            adapter?.updateContents(contents)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapter
            }
        }

        binding.recyclerView.scheduleLayoutAnimation()

        val folderCount = contents.subFolders.size
        val songCount = contents.songs.size

        headerBinding.count.text = buildString {
            if (folderCount > 0) append(getString(R.string.x_folders, folderCount))
            if (folderCount > 0 && songCount > 0) append("  ·  ")
            if (songCount > 0) append(resources.getQuantityString(R.plurals.number_of_songs, songCount, songCount))
            if (folderCount == 0 && songCount == 0) append(getString(R.string.folders_hierarchy))
        }

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
        headerBinding.scroll.hideOnUnfavorableSort(
                sorts = listOf(CommonPreferencesConstants.BY_NAME, CommonPreferencesConstants.BY_PATH),
                preference = FolderHierarchyPreferences.getSortStyle()
        )
        headerBinding.gridSize.setGridSizeValue(FolderHierarchyPreferences.getGridSize())
        headerBinding.gridType.setGridTypeValue(FolderHierarchyPreferences.getGridType())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            FolderHierarchyPreferences.GRID_SIZE_PORTRAIT,
            FolderHierarchyPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(FolderHierarchyPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = FolderHierarchyPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            FolderHierarchyPreferences.GRID_TYPE_PORTRAIT,
            FolderHierarchyPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(FolderHierarchyPreferences.getGridType(), FolderHierarchyPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(FolderHierarchyPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        const val TAG = "FoldersHierarchy"
        private const val KEY_FOLDER_PATH = "folder_path"

        /** Root entry point — no folder path, shows top-level folders. */
        fun newInstance(): FoldersHierarchy {
            return FoldersHierarchy().apply {
                arguments = Bundle()
            }
        }

        /** Opens the contents of [folderPath] in a new hierarchy level. */
        fun newInstance(folderPath: String): FoldersHierarchy {
            return FoldersHierarchy().apply {
                arguments = Bundle().apply {
                    putString(KEY_FOLDER_PATH, folderPath)
                }
            }
        }
    }
}
