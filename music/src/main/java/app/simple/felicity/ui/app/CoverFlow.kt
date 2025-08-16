package app.simple.felicity.ui.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.databinding.FragmentCoverflowBinding
import app.simple.felicity.decorations.coverflow.CoverFlowRenderer
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.repository.models.Song
import app.simple.felicity.shared.utils.ConditionUtils.isNotZero
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class CoverFlow : ScopedFragment() {

    private lateinit var binding: FragmentCoverflowBinding
    private val songsViewModel: SongsViewModel by viewModels({ requireActivity() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentCoverflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireLightBarIcons()

        songsViewModel.getSongAndArt().observe(viewLifecycleOwner) { songs ->
            binding.coverflow.setUris(songs.keys.toList())
            binding.coverflow.scrollToIndex(songsViewModel.getCarouselPosition()).also {
                if (songsViewModel.getCarouselPosition().isNotZero()) {
                    binding.coverflow.reloadTextures()
                }
            }

            binding.coverflow.addScrollListener(object : CoverFlowRenderer.ScrollListener {
                override fun onCenteredIndexChanged(index: Int) {
                    songsViewModel.setCarouselPosition(index)
                    updateInfo(index, songs.values.elementAtOrNull(index))
                }

                override fun onScrollOffsetChanged(offset: Float) {

                }

                override fun onSnapFinished(finalIndex: Int) {

                }

                override fun onSnapStarted(targetIndex: Int) {

                }
            })

            binding.arrowLeft.setOnClickListener {
                binding.coverflow.scrollToIndex(binding.coverflow.getCenteredIndex() - 10)
            }

            binding.arrowRight.setOnClickListener {
                binding.coverflow.scrollToIndex(binding.coverflow.getCenteredIndex() + 10)
            }
        }
    }

    private fun updateInfo(position: Int, song: Song?) {
        if (song != null) {
            binding.title.text = song.title
            binding.artist.text = song.artist
        }
    }

    companion object {
        fun newInstance(): CoverFlow {
            val args = Bundle()
            val fragment = CoverFlow()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "CoverFlow"
    }
}