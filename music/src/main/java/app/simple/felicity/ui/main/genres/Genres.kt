package app.simple.felicity.ui.main.genres

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.genres.AdapterGenres
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.core.utils.TextViewUtils.setStartDrawable
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.databinding.HeaderGenresBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.decorations.views.SharedScrollViewPopup
import app.simple.felicity.dialogs.genres.DialogGenreMenu.Companion.showGenreMenu
import app.simple.felicity.dialogs.genres.DialogGenreSort.Companion.showGenresSortDialog
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortStyle
import app.simple.felicity.viewmodels.main.genres.GenresViewModel

class Genres : MediaFragment() {

    private val genresViewModel: GenresViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentGenresBinding
    private lateinit var headerBinding: HeaderGenresBinding

    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var adapterGenres: AdapterGenres

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentGenresBinding.inflate(inflater, container, false)
        headerBinding = HeaderGenresBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        SlideFastScroller.attach(binding.recyclerView)
        binding.recyclerView.requireAttachedMiniPlayer()
        postponeEnterTransition()

        genresViewModel.getGenresData().observe(viewLifecycleOwner) { genres ->
            Log.d(TAG, "onViewCreated: Genres: ${genres.size}")
            adapterGenres = AdapterGenres(genres)
            adapterGenres.setHasStableIds(true)

            gridLayoutManager = GridLayoutManager(requireContext(), GenresPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager

            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.adapter = adapterGenres

            adapterGenres.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showGenreMenu()
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    Log.d(TAG, "onGenreClicked: Genre: ${genre.name}")
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })

            headerBinding.count.text = getString(R.string.x_genres, genres.size)
            headerBinding.sortStyle.setCurrentSortStyle()
            headerBinding.sortOrder.setCurrentSortOrder()

            headerBinding.menu.setOnClickListener {
                childFragmentManager.showGenreMenu()
            }

            headerBinding.sortOrder.setOnClickListener {
                childFragmentManager.showGenresSortDialog()
            }

            headerBinding.sortStyle.setOnClickListener {
                childFragmentManager.showGenresSortDialog()
            }

            setGridSizeValue()
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
                                R.string.one -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_ONE)
                                R.string.two -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_TWO)
                                R.string.three -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_THREE)
                                R.string.four -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FOUR)
                                R.string.five -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_FIVE)
                                R.string.six -> GenresPreferences.setGridSize(CommonPreferencesConstants.GRID_SIZE_SIX)
                            }
                        },
                        onDismiss = {

                        }
                ).show()
            }

            view.startTransitionOnPreDraw()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE -> {
                setGridSizeValue()
                binding.recyclerView.beginDelayedTransition()
                gridLayoutManager.spanCount = GenresPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.SHOW_GENRE_COVERS -> {
                adapterGenres.notifyDataSetChanged()
            }
        }
    }

    private fun setGridSizeValue() {
        // Set the grid size value based on user preferences
        val gridSize = GenresPreferences.getGridSize()
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
        const val TAG = "GenresFragment"

        fun newInstance(): Genres {
            val args = Bundle()
            val fragment = Genres()
            fragment.arguments = args
            return fragment
        }
    }
}