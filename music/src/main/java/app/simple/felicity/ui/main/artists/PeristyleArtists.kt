package app.simple.felicity.ui.main.artists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.ui.lists.artists.AdapterPeristyleArtists
import app.simple.felicity.adapters.ui.lists.artists.AdapterPeristyleArtists.Companion.AdapterPeristyleArtistsListener
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.viewmodels.main.artists.ArtistsViewModel

class PeristyleArtists : ScopedFragment() {

    private var binding: FragmentAlbumsBinding? = null
    private val albumsViewModel: ArtistsViewModel? by viewModels({ requireActivity() })
    private var adapter: AdapterPeristyleArtists? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding!!.root
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

            adapter?.setAdapterPeristyleArtistsListener(object : AdapterPeristyleArtistsListener {
                override fun onArtistClick(artist: Artist, view: View) {

                }
            })
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
