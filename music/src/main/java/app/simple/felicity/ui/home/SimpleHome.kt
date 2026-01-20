package app.simple.felicity.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.decorations.views.AppHeader
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.ui.panels.Albums
import app.simple.felicity.ui.panels.ArtFlow
import app.simple.felicity.ui.panels.Artists
import app.simple.felicity.ui.panels.Genres
import app.simple.felicity.ui.panels.Preferences
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel.Companion.Element

class SimpleHome : MediaFragment() {

    private lateinit var binding: FragmentHomeSimpleBinding

    private var homeViewModel: SimpleHomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        binding.recyclerView.requireAttachedMiniPlayer()
        binding.appHeader.attachTo(binding.recyclerView, AppHeader.ScrollMode.HIDE_ON_SCROLL)

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            binding.recyclerView.adapter = AdapterSimpleHome(list)

            (binding.recyclerView.adapter as AdapterSimpleHome).setAdapterSimpleHomeCallbacks(object : AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks {
                override fun onItemClicked(element: Element, position: Int, view: View) {
                    when (element.titleResId) {
                        R.string.songs -> {
                            navigateToSongsFragment()
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

            view.startTransitionOnPreDraw()
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
