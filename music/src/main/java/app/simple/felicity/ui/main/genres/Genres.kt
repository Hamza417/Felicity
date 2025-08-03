package app.simple.felicity.ui.main.genres

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.genres.AdapterGenres
import app.simple.felicity.databinding.FragmentGenresBinding
import app.simple.felicity.decorations.itemdecorations.GridSpacingItemDecoration
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.viewmodels.main.genres.GenresViewModel

class Genres : ScopedFragment() {

    private var genresViewModel: GenresViewModel? = null

    private lateinit var binding: FragmentGenresBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        binding = FragmentGenresBinding.inflate(inflater, container, false)
        genresViewModel = ViewModelProvider(requireActivity())[GenresViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        genresViewModel!!.getGenresData().observe(viewLifecycleOwner) { genres ->
            Log.d(TAG, "onViewCreated: Genres: ${genres.size}")
            val adapter = AdapterGenres(genres)

            binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.recyclerView.addItemDecoration(
                    GridSpacingItemDecoration
                    ((binding.recyclerView.layoutManager as GridLayoutManager).spanCount,
                     resources.getDimensionPixelOffset(R.dimen.padding_15),
                     true,
                     0))
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.adapter = adapter

            adapter.setGenreClickListener(object : AdapterGenres.Companion.GenreClickListener {
                override fun onGenreClicked(genre: Genre, view: View) {
                    findNavController().navigate(GenresDirections.actionGenresToPage(genre))
                }
            })
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