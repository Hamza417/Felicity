package app.simple.felicity.ui.main.genres

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.forEach
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.TransitionManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.genres.AdapterGenres
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.databinding.HeaderGenresBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.itemdecorations.GridSpacingItemDecoration
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.genres.DialogGenreMenu.Companion.showGenreMenu
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.viewmodels.main.genres.GenresViewModel
import com.bumptech.glide.Glide

class Genres : ScopedFragment() {

    private val genresViewModel: GenresViewModel by viewModels({ requireActivity() })

    private lateinit var binding: FragmentGenresBinding
    private lateinit var headerBinding: HeaderGenresBinding

    private lateinit var gridLayoutManager: GridLayoutManager

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
        postponeEnterTransition()

        genresViewModel.getGenresData().observe(viewLifecycleOwner) { genres ->
            Log.d(TAG, "onViewCreated: Genres: ${genres.size}")
            val adapter = AdapterGenres(genres)
            adapter.setHasStableIds(true)

            gridLayoutManager = GridLayoutManager(requireContext(), GenresPreferences.getGridSize())
            binding.recyclerView.layoutManager = gridLayoutManager

            binding.recyclerView.addItemDecoration(GridSpacingItemDecoration(
                    gridLayoutManager.spanCount,
                    resources.getDimensionPixelOffset(R.dimen.padding_15),
                    resources.getDimensionPixelOffset(R.dimen.padding_15),
                    true,
                    0))
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.adapter = adapter

            adapter.setCallbackListener(object : GeneralAdapterCallbacks {
                override fun onMenuClicked(view: View) {
                    childFragmentManager.showGenreMenu()
                }

                override fun onGenreClicked(genre: Genre, view: View) {
                    Log.d(TAG, "onGenreClicked: Genre: ${genre.name}")
                    openFragment(GenrePage.newInstance(genre), GenrePage.TAG)
                }
            })

            headerBinding.menu.setOnClickListener {
                childFragmentManager.showGenreMenu()
            }

            view.startTransitionOnPreDraw()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE -> {
                Log.d(TAG, "onSharedPreferenceChanged: Grid Size Changed")
                binding.recyclerView.forEach {
                    if (it is ImageView) {
                        Glide.with(it).clear(it)
                    }
                }
                TransitionManager.beginDelayedTransition(binding.recyclerView)
                gridLayoutManager.spanCount = GenresPreferences.getGridSize()
                binding.recyclerView.adapter?.notifyItemRangeChanged(0, binding.recyclerView.adapter?.itemCount ?: 0)
            }
            GenresPreferences.GRID_SPACING -> {

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