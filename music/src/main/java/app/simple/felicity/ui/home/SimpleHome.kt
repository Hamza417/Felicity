package app.simple.felicity.ui.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.databinding.HeaderHomeBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.ArtFlow
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Folders
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.Preferences
import app.simple.felicity.ui.panels.Search
import app.simple.felicity.ui.panels.Songs
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel.Companion.Element

class SimpleHome : MediaFragment() {

    private lateinit var binding: FragmentHomeSimpleBinding
    private lateinit var headerBinding: HeaderHomeBinding

    private var homeViewModel: SimpleHomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)
        headerBinding = HeaderHomeBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.setContentView(headerBinding.root)
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        headerBinding.search.setOnClickListener {
            Log.d(TAG, "onViewCreated: Search Clicked")
            openFragment(Search.newInstance(), Search.TAG)
        }

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            binding.recyclerView.adapter = AdapterSimpleHome(list)

            (binding.recyclerView.adapter as AdapterSimpleHome).setAdapterSimpleHomeCallbacks(object : AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks {
                override fun onItemClicked(element: Element, position: Int, view: View) {
                    when (element.titleResId) {
                        R.string.songs -> {
                            openFragment(Songs.newInstance(), Songs.TAG)
                        }

                        R.string.albums -> {
                            openFragment(Albums.newInstance(), Albums.TAG)
                        }

                        R.string.artists -> {
                            openFragment(Artists.newInstance(), Artists.TAG)
                        }
                        R.string.genres -> {
                            openFragment(Genres.newInstance(), Genres.TAG)
                        }
                        R.string.folders -> {
                            openFragment(Folders.newInstance(), Folders.TAG)
                        }
                        R.string.preferences -> {
                            openFragment(Preferences.newInstance(), Preferences.TAG)
                        }
                        else -> {
                            // Handle other cases or show a message
                        }
                    }
                }

                override fun onCarouselClicked(element: Element, position: Int, view: View) {
                    when (element.titleResId) {
                        R.string.songs -> {
                            openFragment(ArtFlow.newInstance(), ArtFlow.TAG)
                        }
                        else -> {
                            // Handle other cases or show a message
                        }
                    }
                }
            })
        }
    }

    companion object {
        fun newInstance(): SimpleHome {
            val args = Bundle()
            val fragment = SimpleHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "SimpleHome"
    }
}
