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
import app.simple.felicity.adapters.ui.lists.AdapterGenres
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.databinding.HeaderGenresBinding
import app.simple.felicity.decorations.fastscroll.SectionedFastScroller
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.genres.GenreMenu.Companion.showGenreMenu
import app.simple.felicity.dialogs.genres.GenreSort.Companion.showGenresSortDialog
import app.simple.felicity.extensions.fragments.PanelFragment
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.GenreSort.setCurrentSortStyle
import app.simple.felicity.ui.pages.GenrePage
import app.simple.felicity.viewmodels.panels.GenresViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panel fragment displaying the user's genres with sort, grid layout, and search support.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class Genres : PanelFragment() {

    private val genresViewModel: GenresViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentGenresBinding
    private lateinit var headerBinding: HeaderGenresBinding

    private var gridLayoutManager: GridLayoutManager? = null
    private var adapterGenres: AdapterGenres? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenresBinding.inflate(inflater, container, false)
        headerBinding = HeaderGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.header.setContentView(headerBinding.root)
        binding.header.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)
        binding.recyclerView.attachSlideFastScroller()
        binding.recyclerView.requireAttachedMiniPlayer()

        val mode = GenresPreferences.getGridSize()
        gridLayoutManager = GridLayoutManager(requireContext(), mode.spanCount)
        binding.recyclerView.layoutManager = gridLayoutManager

        adapterGenres?.let { binding.recyclerView.adapter = it }

        setupClickListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                genresViewModel.genres.collect { genres ->
                    if (genres.isNotEmpty()) {
                        updateGenresList(genres)
                    } else if (adapterGenres != null) {
                        updateGenresList(genres)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        gridLayoutManager = null
        super.onDestroyView()
    }

    private fun setupClickListeners() {
        headerBinding.menu.setOnClickListener {
            childFragmentManager.showGenreMenu()
        }

        headerBinding.sortOrder.setOnClickListener {
            childFragmentManager.showGenresSortDialog()
        }

        headerBinding.sortStyle.setOnClickListener {
            childFragmentManager.showGenresSortDialog()
        }

        headerBinding.search.setOnClickListener {
            openSearch()
        }
    }

    private fun updateGenresList(genres: List<Genre>) {
        if (adapterGenres == null) {
            adapterGenres = AdapterGenres(genres)
            adapterGenres?.setHasStableIds(true)
            adapterGenres?.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showGenreMenu()
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })
            binding.recyclerView.adapter = adapterGenres
        } else {
            adapterGenres?.updateList(genres)
            if (binding.recyclerView.adapter == null) {
                binding.recyclerView.adapter = adapterGenres
            }
        }

        headerBinding.count.text = getString(R.string.x_genres, genres.size)
        binding.recyclerView.requireAttachedSectionScroller(
                sections = provideScrollPositionDataBasedOnSortStyle(genres),
                header = binding.header,
                view = headerBinding.scroll)

        headerBinding.sortStyle.setCurrentSortStyle()
        headerBinding.sortOrder.setCurrentSortOrder()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE_PORTRAIT, GenresPreferences.GRID_SIZE_LANDSCAPE -> {
                val newMode = GenresPreferences.getGridSize()
                gridLayoutManager?.spanCount = newMode.spanCount
                binding.recyclerView.beginDelayedTransition()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.SHOW_GENRE_COVERS -> {
                adapterGenres?.notifyDataSetChanged()
            }
        }
    }

    private fun provideScrollPositionDataBasedOnSortStyle(genres: List<Genre>): List<SectionedFastScroller.Position> {
        return when (GenresPreferences.getSortStyle()) {
            CommonPreferencesConstants.BY_NAME -> {
                val firstAlphabetToIndex = linkedMapOf<String, Int>()
                genres.forEachIndexed { index, genre ->
                    val firstChar = genre.name?.firstOrNull()?.uppercaseChar()
                    val key = if (firstChar != null && firstChar.isLetter()) firstChar.toString() else "#"
                    if (!firstAlphabetToIndex.containsKey(key)) firstAlphabetToIndex[key] = index
                }
                firstAlphabetToIndex.map { (char, index) -> SectionedFastScroller.Position(char, index) }
            }
            else -> listOf()
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