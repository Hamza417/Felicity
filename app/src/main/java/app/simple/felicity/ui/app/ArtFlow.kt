package app.simple.felicity.ui.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.SongsAdapter
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