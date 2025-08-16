package app.simple.felicity.ui.main.albums

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import app.simple.felicity.adapters.ui.lists.albums.AdapterPeristyleAlbums
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.albums.AlbumsViewModel

class PeristyleAlbums : ScopedFragment() {

    private var binding: FragmentAlbumsBinding? = null
    private var albumsViewModel: AlbumsViewModel? = null
    private var adapter: AdapterPeristyleAlbums? = null
    private var gridLayoutManager: GridLayoutManager? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val fragmentBinding = FragmentAlbumsBinding.inflate(inflater, container, false)

        binding = fragmentBinding
        albumsViewModel = ViewModelProvider(requireActivity())[AlbumsViewModel::class.java]
        binding?.recyclerView?.setBackgroundColor(Color.BLACK)
        requireLightBarIcons()

        return fragmentBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        albumsViewModel?.getAlbums()?.observe(viewLifecycleOwner) { albums ->
            adapter = AdapterPeristyleAlbums(albums)

            gridLayoutManager = GridLayoutManager(context, 2)
            gridLayoutManager?.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position % 5 == 0) 2 else 1
                }
            }

            binding?.recyclerView?.layoutManager = gridLayoutManager
            binding?.recyclerView?.adapter = adapter
            binding?.recyclerView?.backgroundTintList = null

            startPostViewTransition(requireView())
        }
    }

    companion object {
        fun newInstance(): PeristyleAlbums {
            val args = Bundle()
            val fragment = PeristyleAlbums()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "PeristyleAlbums"
    }
}
