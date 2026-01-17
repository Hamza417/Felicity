package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.artists.AdapterArtists
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentArtistsBinding
import app.simple.felicity.databinding.HeaderArtistsBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.songs.ArtistsSort.Companion.showArtistsSort
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.ArtistPreferences
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.ArtistSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.viewmodels.main.artists.ArtistsViewModel

class Artists : PanelFragment() {

    private lateinit var binding: FragmentArtistsBinding
    private lateinit var headerBinding: HeaderArtistsBinding

    private lateinit var adapterArtists: AdapterArtists

    private lateinit var gridLayoutManager: GridLayoutManager

    private val artistViewModel: ArtistsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentArtistsBinding.inflate(inflater, container, false)
        headerBinding = HeaderArtistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        artistViewModel.getArtists().observe(viewLifecycleOwner) { artists ->
            binding.recyclerView.requireAttachedSectionScroller(
                    sections = provideScrollPositionDataBasedOnSortStyle(artists = artists),
                    header = binding.header,
                    view = headerBinding.scroll)

            gridLayoutManager = GridLayoutManager(requireContext(), ArtistPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.setGridType(ArtistPreferences.getGridType(), ArtistPreferences.getGridSize())
            adapterArtists = AdapterArtists(artists)
            binding.recyclerView.adapter = adapterArtists

            adapterArtists.setGeneralAdapterCallbacks(object : GeneralAdapterCallbacks {
                override fun onArtistClicked(artists: List<Artist>, position: Int, view: View) {
                    openFragment(ArtistPage.newInstance(artists[position]), ArtistPage.TAG)
                }
            })

            headerBinding.count.text = getString(R.string.x_artists, artists.size)
            headerBinding.sortOrder.setCurrentSortOrder()
            headerBinding.sortStyle.setCurrentSortStyle()

            headerBinding.scroll.hideOnUnfavorableSort(
                    listOf(CommonPreferencesConstants.BY_NAME),
                    ArtistPreferences.getArtistSort()
            )

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showArtistsSort()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showArtistsSort()
            }

            headerBinding.gridSize.setGridSizeValue(ArtistPreferences.getGridSize())
            headerBinding.gridType.setGridTypeValue(ArtistPreferences.getGridType())

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
                                R.string.one -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                                R.string.two -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                                R.string.three -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                                R.string.four -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                                R.string.five -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                                R.string.six -> ArtistPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
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
                                R.string.list -> ArtistPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_LIST)
                                R.string.grid -> ArtistPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_GRID)
                                R.string.peristyle -> ArtistPreferences.setGridType(CommonPreferencesConstants.GRID_TYPE_PERISTYLE)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            view.startTransitionOnPreDraw()
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(artists: List<Artist>): List<SectionedFastScroller.Position> {
        when (ArtistPreferences.getArtistSort()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                artists.forEachIndexed { index, artist ->
                    val firstChar = artist.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) {
                        firstChar.toString()
                    } else {
                        "#"
                    }
                    if (!firstAlphabetToIndex.containsKey(key)) {
                        firstAlphabetToIndex[key] = index
                    }
                }
                return firstAlphabetToIndex.map { (char, index) ->
                    SectionedFastScroller.Position(char, index)
                }
            }
        }

        return emptyList()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ArtistPreferences.GRID_SIZE_PORTRAIT, ArtistPreferences.GRID_SIZE_LANDSCAPE -> {
                headerBinding.gridSize.setGridSizeValue(ArtistPreferences.getGridSize())
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager.spanCount = ArtistPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            ArtistPreferences.GRID_TYPE_PORTRAIT, ArtistPreferences.GRID_TYPE_LANDSCAPE -> {
                binding.recyclerView.setGridType(ArtistPreferences.getGridType(), ArtistPreferences.getGridSize())
                headerBinding.gridType.setGridTypeValue(ArtistPreferences.getGridType())
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
        }
    }

    companion object {
        fun newInstance(): Artists {
            val args = Bundle()
            val fragment = Artists()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Artists"
    }
}