package app.simple.felicity.ui.main.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.ui.lists.artists.AdapterPeristyleArtists
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.artists.ArtistsViewModel

class PeristyleArtists : ScopedFragment() {

    private var binding: FragmentAlbumsBinding? = null
    private var albumsViewModel: ArtistsViewModel? = null
    private var adapter: AdapterPeristyleArtists? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val fragmentBinding = FragmentAlbumsBinding.inflate(inflater, container, false)

        binding = fragmentBinding
        albumsViewModel = ViewModelProvider(requireActivity())[ArtistsViewModel::class.java]

        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumsViewModel?.getArtists()?.observe(viewLifecycleOwner) { albums ->
            adapter = AdapterPeristyleArtists(albums)

            gridLayoutManager = GridLayoutManager(context, 2)
            gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position % 5 == 0) 2 else 1
                }
            }

            binding?.recyclerView?.layoutManager = gridLayoutManager
            binding?.recyclerView?.adapter = adapter
            startPostViewTransition(requireView())
        }
    }

    companion object {
        fun newInstance(): PeristyleArtists {
            val args = Bundle()
            val fragment = PeristyleArtists()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "PeristyleArtists"
    }
}
