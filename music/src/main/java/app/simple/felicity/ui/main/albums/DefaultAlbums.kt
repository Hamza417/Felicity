package app.simple.felicity.ui.main.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.albums.AdapterDefaultAlbums
import app.simple.felicity.callbacks.GeneralAdapterCallbacks
import app.simple.felicity.databinding.FragmentAlbumsBinding
import app.simple.felicity.databinding.HeaderAlbumsBinding
import app.simple.felicity.decorations.fastscroll.SlideFastScroller
import app.simple.felicity.decorations.itemanimators.FlipItemAnimator
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.dialogs.albums.AlbumsSort.Companion.showAlbumsSort
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortOrder
import app.simple.felicity.repository.sort.AlbumSort.setCurrentSortStyle
import app.simple.felicity.viewmodels.main.albums.AlbumsViewModel

class DefaultAlbums : MediaFragment() {

    private lateinit var binding: FragmentAlbumsBinding
    private lateinit var headerBinding: HeaderAlbumsBinding

    private var adapterDefaultAlbums: AdapterDefaultAlbums? = null

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

        albumsViewModel.getAlbums().observe(viewLifecycleOwner) {
            adapterDefaultAlbums = AdapterDefaultAlbums(it)
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
        }
    }

    companion object {
        fun newInstance(): DefaultAlbums {
            val args = Bundle()
            val fragment = DefaultAlbums()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "DefaultAlbums"
    }
}