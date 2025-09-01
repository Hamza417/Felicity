package app.simple.felicity.ui.main.albums

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.albums.AdapterDefaultAlbums
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.databinding.HeaderAlbumsBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.itemanimators.FlipItemAnimator
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

    private var adapterDefaultAlbums: AdapterDefaultAlbums? = null
    private var gridLayoutManager: GridLayoutManager? = null

    private val albumsViewModel: AlbumsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        headerBinding = HeaderAlbumsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.itemAnimator = FlipItemAnimator()
        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()

        albumsViewModel.getAlbums().observe(viewLifecycleOwner) { it ->
            adapterDefaultAlbums = AdapterDefaultAlbums(it)
            gridLayoutManager = GridLayoutManager(requireContext(), AlbumPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager
            adapterDefaultAlbums?.setHasStableIds(true)
            binding.recyclerView.adapter = adapterDefaultAlbums
            headerBinding.count.text = getString(R.string.x_albums, it.size)

            adapterDefaultAlbums?.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
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
            headerBinding.gridSize.setOnClickListener {
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
            }
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