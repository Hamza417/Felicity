package app.simple.felicity.ui.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.SongsAdapter
import app.simple.felicity.constants.BundleConstants
import app.simple.felicity.decorations.carousel.ArtFlowCarousel
import app.simple.felicity.viewmodels.ui.SongsViewModel
import app.simple.inure.extensions.fragments.ScopedFragment

class ArtFlow : ScopedFragment() {

    private lateinit var coverFlow: ArtFlowCarousel
    private lateinit var songsViewModel: SongsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_artflow, container, false)

        coverFlow = view.findViewById(R.id.artflow)

        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            coverFlow.adapter = SongsAdapter(requireContext(), it)

            coverFlow.setOnScrollPositionListener(object : ArtFlowCarousel.OnScrollPositionListener {
                override fun onScrolledToPosition(position: Int) {
                    Log.d("ArtFlow", "Scrolled to position: $position")
                }

                override fun onScrolling() {
                    Log.d("ArtFlow", "Scrolling")
                }
            })

            coverFlow.setOnItemSelectedListener { child, position ->
                Log.d("ArtFlow", "Selected item: $position")
                requireArguments().putInt(BundleConstants.position, position)
            }

            coverFlow.scrollToPosition(requireArguments().getInt(BundleConstants.position, 0))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Log.d("ArtFlow", "Saving state")
        Log.d("ArtFlow", "Current position: ${coverFlow.currentItemPosition}")
        outState.putInt(BundleConstants.position, coverFlow.currentItemPosition)
        super.onSaveInstanceState(outState)
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