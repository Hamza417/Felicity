package app.simple.felicity.ui.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.artflow.SongsAdapter
import app.simple.felicity.constants.BundleConstants
import app.simple.felicity.databinding.FragmentArtflowBinding
import app.simple.felicity.decorations.carousel.ArtFlowCarousel
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class ArtFlow : ScopedFragment() {

    private var binding: FragmentArtflowBinding? = null
    private lateinit var songsViewModel: SongsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_artflow, container, false)

        binding = FragmentArtflowBinding.bind(view)
        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            binding?.artflow?.adapter = SongsAdapter(requireContext(), it)

            binding?.artflow?.setOnScrollPositionListener(object : ArtFlowCarousel.OnScrollPositionListener {
                override fun onScrolledToPosition(position: Int) {
                    Log.d("ArtFlow", "Scrolled to position: $position")
                }

                override fun onScrolling() {
                    Log.d("ArtFlow", "Scrolling")
                }
            })

            binding?.artflow?.setOnItemSelectedListener { child, position ->
                Log.d("ArtFlow", "Selected item: $position")
                requireArguments().putInt(BundleConstants.position, position)

                binding?.title?.text = it[position].title
                binding?.artist?.text = it[position].artist
            }

            binding?.artflow?.scrollToPosition(requireArguments().getInt(BundleConstants.position, 0))
        }
    }

    companion object {
        fun newInstance(): ArtFlow {
            val args = Bundle()
            val fragment = ArtFlow()
            fragment.arguments = args
            return fragment
        }
    }
}
