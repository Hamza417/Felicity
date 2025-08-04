package app.simple.felicity.ui.main.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.simple.felicity.adapters.home.main.AdapterCarouselHome
import app.simple.felicity.databinding.FragmentHomeCarouselBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.viewmodels.main.home.HomeViewModel
import kotlinx.coroutines.launch

class CarouselHome : MediaFragment() {

    private var binding: FragmentHomeCarouselBinding? = null
    private val homeViewModel: HomeViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeCarouselBinding.inflate(inflater, container, false)
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            homeViewModel.data.collect { data ->
                val adapter = AdapterCarouselHome(data)
                binding?.recyclerView?.addItemDecoration(SpacingItemDecoration(48, true))
                binding?.recyclerView?.adapter = adapter
                binding?.recyclerView?.setHasFixedSize(true)

                adapter.setAdapterCarouselHomeCallbacks(object : AdapterCarouselHome.Companion.AdapterCarouselCallbacks {
                    override fun onSubItemClicked(view: View, position: Int, itemPosition: Int) {
                        Log.d(TAG, "Item clicked at position: $position")
                        when (data[position].items[0]) {
                            is Song -> {
                                setMediaItems(data[position].items.filterIsInstance<Song>(), itemPosition)
                            }
                            is Genre -> {
                                val genre = data[position].items.filterIsInstance<Genre>()[itemPosition]
                                val action = CarouselHomeDirections.actionGenresToPage(genre)
                                findNavController().navigate(action)
                            }
                            else -> {
                                Log.w(TAG, "Unsupported item type clicked at position: $position")
                            }
                        }
                    }

                    override fun onClicked(view: View, position: Int) {
                        Log.d(TAG, "Carousel clicked at position: $position")
                        when (data[position].items[0]) {
                            is Song -> {
                                // findNavController().navigate(CarouselHomeDirections.actionHomeToSongs())
                                findNavController().navigate(CarouselHomeDirections.actionHomeToCarouselFlow())
                            }
                            is Genre -> {
                                findNavController().navigate(CarouselHomeDirections.actionHomeToGenres())
                            }
                            else -> {
                                Log.w(TAG, "Unsupported item type clicked at position: $position")
                            }
                        }
                    }
                })
            }
        }
    }

    companion object {
        fun newInstance(): CarouselHome {
            val args = Bundle()
            val fragment = CarouselHome()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "CarouselHome"
    }
}