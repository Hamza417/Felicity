package app.simple.felicity.ui.main.albums

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.albums.AdapterAlbums
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.databinding.HeaderAlbumsBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.albums.AlbumsSort.Companion.showAlbumsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.AlbumPreferences
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortStyle
import app.simple.felicity.viewmodels.main.albums.AlbumsViewModel

class Albums : MediaFragment() {

    private lateinit var binding: FragmentAlbumsBinding
    private lateinit var headerBinding: HeaderAlbumsBinding

    private var adapterAlbums: AdapterAlbums? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val albumsViewModel: AlbumsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlbumsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()

        albumsViewModel.getAlbums().observe(viewLifecycleOwner) { it ->
            adapterAlbums = AdapterAlbums(it)
            gridLayoutManager = GridLayoutManager(requireContext(), AlbumPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager
            adapterAlbums?.setHasStableIds(true)
            headerBinding.count.text = getString(R.string.x_albums, it.size)

            adapterAlbums?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onAlbumClicked(albums: List<Album>, position: Int, view: View?) {
                    openFragment(AlbumPage.newInstance(albums[position]), AlbumPage.TAG)
                }
            })

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showAlbumsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showAlbumsSort()
            }

            headerBinding.sortStyle.setCurrentSortStyle()
            headerBinding.sortOrder.setCurrentSortOrder()

            setGridSizeValue()
            setGridTypeValue()

            headerBinding.gridSize.setOnClickListener { button ->
                SharedScrollViewPopup(
                        container = requireContainerView(),
                        anchorView = button,
                        menuItems = listOf(R.string.one,
                                           R.string.two,
                                           R.string.three,
                                           R.string.four,
                                           R.string.five,
                                           R.string.six),
                        menuIcons = listOf(R.drawable.ic_one_16,
                                           R.drawable.ic_two_16dp,
                                           R.drawable.ic_three_16dp,
                                           R.drawable.ic_four_16dp,
                                           R.drawable.ic_five_16dp,
                                           R.drawable.ic_six_16dp),
                        onMenuItemClick = {
                            when (it) {
                                R.string.one -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                                R.string.two -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                                R.string.three -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                                R.string.four -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                                R.string.five -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                                R.string.six -> AlbumPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            headerBinding.gridType.setOnClickListener { button ->
                SharedScrollViewPopup(
                        container = requireContainerView(),
                        anchorView = button,
                        menuItems = listOf(
                                R.string.list,
                                R.string.grid,
                                R.string.peristyle,
                        ),
                        menuIcons = listOf(
                                R.drawable.ic_list_16dp,
                                R.drawable.ic_grid_16dp,
                                R.drawable.ic_peristyle_16dp,
                        ),
                        onMenuItemClick = {
                            when (it) {
                                R.string.list -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                                R.string.grid -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                                R.string.peristyle -> AlbumPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_PERISTYLE)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            binding.recyclerView.adapter = adapterAlbums
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            AlbumPreferences.GRID_SIZE -> {
                setGridSizeValue()
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager?.spanCount = AlbumPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            AlbumPreferences.GRID_TYPE -> {
                setGridTypeValue()
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    private fun setGridSizeValue() {
        val gridSize = AlbumPreferences.getGridSize()
        when (gridSize) {
            CommonPreferencesConstants.GRID_SIZE_ONE -> {
                headerBinding.gridSize.text = getString(R.string.one)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_one_16)
            }
            CommonPreferencesConstants.GRID_SIZE_TWO -> {
                headerBinding.gridSize.text = getString(R.string.two)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_two_16dp)
            }
            CommonPreferencesConstants.GRID_SIZE_THREE -> {
                headerBinding.gridSize.text = getString(R.string.three)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_three_16dp)
            }
            CommonPreferencesConstants.GRID_SIZE_FOUR -> {
                headerBinding.gridSize.text = getString(R.string.four)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_four_16dp)
            }
            CommonPreferencesConstants.GRID_SIZE_FIVE -> {
                headerBinding.gridSize.text = getString(R.string.five)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_five_16dp)
            }
            CommonPreferencesConstants.GRID_SIZE_SIX -> {
                headerBinding.gridSize.text = getString(R.string.six)
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_six_16dp)
            }
            else -> {
                headerBinding.gridSize.text = getString(R.string.two) // Default to two columns
                headerBinding.gridSize.setStartDrawable(R.drawable.ic_two_16dp)
            }
        }
    }

    fun setGridTypeValue() {
        val gridType = AlbumPreferences.getGridType()
        when (gridType) {
            CommonPreferencesConstants.GRID_TYPE_LIST -> {
                headerBinding.gridType.text = getString(R.string.list)
                headerBinding.gridType.setStartDrawable(R.drawable.ic_list_16dp)

                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }

                binding.recyclerView.applySpacing()
            }
            CommonPreferencesConstants.GRID_TYPE_GRID -> {
                headerBinding.gridType.text = getString(R.string.grid)
                headerBinding.gridType.setStartDrawable(R.drawable.ic_grid_16dp)

                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }

                binding.recyclerView.applySpacing()
            }
            CommonPreferencesConstants.GRID_TYPE_PERISTYLE -> {
                headerBinding.gridType.text = getString(R.string.peristyle)
                headerBinding.gridType.setStartDrawable(R.drawable.ic_peristyle_16dp)

                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        val spanCount = maxOf(1, AlbumPreferences.getGridSize())
                        val cycle = spanCount * 2 + 1 // 1 giant + 2 rows of grid
                        return if (position % cycle == 0) spanCount else 1
                    }
                }

                binding.recyclerView.removeSpacing()
            }
            else -> {
                headerBinding.gridType.text = getString(R.string.list) // Default to list
                headerBinding.gridType.setStartDrawable(R.drawable.ic_list_16dp)

                gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int = 1
                }
            }
        }
    }

    companion object {
        fun newInstance(): Albums {
            val args = Bundle()
            val fragment = Albums()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "DefaultAlbums"
    }
}