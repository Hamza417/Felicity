package app.simple.felicity.ui.main.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.models.Element
import app.simple.felicity.ui.main.albums.PeristyleAlbums
import app.simple.felicity.ui.main.artists.PeristyleArtists
import app.simple.felicity.ui.main.songs.InureSongs
import app.simple.felicity.viewmodels.main.home.InureHomeViewModel

class InureHome : ScopedFragment() {

    private lateinit var recyclerView: CustomVerticalRecyclerView

    private var homeViewModel: InureHomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)

        recyclerView = binding.recyclerView
        homeViewModel = ViewModelProvider(requireActivity())[InureHomeViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { list ->
            recyclerView.adapter = AdapterSimpleHome(list)

            (recyclerView.adapter as AdapterSimpleHome).setAdapterSimpleHomeCallbacks(object : AdapterSimpleHome.Companion.AdapterSimpleHomeCallbacks {
                override fun onItemClicked(element: Element, position: Int, view: View) {
                    when (element.title) {
                        R.string.songs -> {
                            openFragmentArc(InureSongs.newInstance(), view, InureSongs.TAG)
                        }

                        R.string.albums -> {
                            openFragmentArc(PeristyleAlbums.newInstance(), view, PeristyleAlbums.TAG)
                        }

                        R.string.artists -> {
                            openFragmentArc(PeristyleArtists.newInstance(), view, PeristyleArtists.TAG)
                        }
                    }
                }
            })

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }
    }

    companion object {
        fun newInstance(): InureHome {
            val args = Bundle()
            val fragment = InureHome()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "Inure_Home"
    }
}
