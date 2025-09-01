package app.simple.felicity.ui.main.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView
import app.simple.felicity.dialogs.home.HomeMenu.Companion.showHomeMenu
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.ui.main.albums.Albums
import app.simple.felicity.ui.main.artists.Artists
import app.simple.felicity.ui.main.genres.Genres
import app.simple.felicity.ui.main.preferences.Preferences
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel
import app.simple.felicity.viewmodels.main.home.SimpleHomeViewModel.Companion.Element

class SimpleHome : MediaFragment() {

    private lateinit var recyclerView: CustomVerticalRecyclerView

    private var homeViewModel: SimpleHomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)

        recyclerView = binding.recyclerView
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        recyclerView.requireAttachedMiniPlayer()

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            recyclerView.adapter = AdapterSimpleHome(list)

            (recyclerView.adapter as AdapterSimpleHome).setAdapterSimpleHomeCallbacks(object : AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks {
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

                override fun onMenuClicked(view: View) {
                    childFragmentManager.showHomeMenu()
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
