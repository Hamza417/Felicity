package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import app.simple.felicity.databinding.FragmentPlayerCarouselBinding
import app.simple.felicity.databinding.FragmentPlayerFadedWaveformBinding
import app.simple.felicity.decorations.highlight.HighlightTextView
import app.simple.felicity.decorations.lrc.view.LrcLineView
import app.simple.felicity.decorations.pager.CarouselTransformers
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.seekbars.WaveformSeekbar
import app.simple.felicity.decorations.views.FavoriteButton
import app.simple.felicity.decorations.views.FelicityMediaControls
import app.simple.felicity.decorations.views.FelicityVisualizer
import app.simple.felicity.extensions.fragments.BasePlayerFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Faded player interface. Inflates [FragmentPlayerFadedWaveformBinding] and wires each
 * abstract view property in [BasePlayerFragment] to the corresponding binding field.
 * All playback, seekbar, visualizer, and album-art-pager logic is inherited from the
 * base class.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class CarouselPlayer : BasePlayerFragment() {

    private lateinit var binding: FragmentPlayerCarouselBinding

    override val pager: FelicityPager
        get() = binding.pager

    override val count: TextView
        get() = binding.count

    override val mediaControls: FelicityMediaControls
        get() = binding.mediaControls

    override val queue: View
        get() = binding.queue

    override val search: View
        get() = binding.search

    override val menu: View
        get() = binding.menu

    override val repeat: ImageView
        get() = binding.repeat

    override val pcmInfo: TextView
        get() = binding.pcmInfo

    override val seekbar: WaveformSeekbar
        get() = binding.seekbar

    override val lyrics: View
        get() = binding.lyrics

    override val favorite: FavoriteButton
        get() = binding.favorite

    override val equalizer: View
        get() = binding.equalizer

    override val visualizerButton: View
        get() = binding.visualizerButton

    override val visualizer: FelicityVisualizer
        get() = binding.visualizer

    override val title: TextView
        get() = binding.title

    override val artist: TextView
        get() = binding.artist

    override val album: TextView
        get() = binding.album
    override val shuffle: HighlightTextView
        get() = binding.shuffle

    override val lrc: LrcLineView
        get() = binding.lrc

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPlayerCarouselBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.pager.setCarouselSidePagesVisible(visible = true)
        binding.pager.carouselPageTransformer = CarouselTransformers.depth
    }

    companion object {
        /**
         * Creates a new instance of [CarouselPlayer] with an empty argument bundle.
         *
         * @return a fresh [CarouselPlayer] fragment instance
         */
        fun newInstance(): CarouselPlayer {
            val args = Bundle()
            val fragment = CarouselPlayer()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "CarouselPlayer"
    }
}