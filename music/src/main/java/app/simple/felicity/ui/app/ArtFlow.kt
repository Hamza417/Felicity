package app.simple.felicity.ui.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.adapters.artflow.SongsAdapter
import app.simple.felicity.databinding.FragmentArtflowBinding
import app.simple.felicity.decorations.carousel.ArtFlowCarousel
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class ArtFlow : ScopedFragment() {

    private var binding: FragmentArtflowBinding? = null
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_artflow, container, false)

        binding = FragmentArtflowBinding.bind(view)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            binding?.artflow?.adapter = SongsAdapter(requireContext(), songs)

            binding?.artflow?.setOnScrollPositionListener(object : ArtFlowCarousel.OnScrollPositionListener {
                override fun onScrolledToPosition(position: Int) {
                    Log.d("ArtFlow", "Scrolled to position: $position")
                    requireArguments().putInt(app.simple.felicity.shared.constants.BundleConstants.position, position)
                }

                override fun onScrolling() {
                    Log.d("ArtFlow", "Scrolling")
                }
            })

            binding?.artflow?.setOnItemSelectedListener { child, position ->
                binding?.title?.text = songs[position].title
                binding?.artist?.text = songs[position].artist
            }

            binding?.artflow?.scrollToPosition(requireArguments().getInt(app.simple.felicity.shared.constants.BundleConstants.position, 0))
        }
    }

    companion object {
        private const val TAG = "ArtFlow"
    }
}
