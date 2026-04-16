package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import app.simple.felicity.databinding.FragmentPlayerFadedWaveformBinding
import app.simple.felicity.decorations.lrc.view.LrcLineView
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.seekbars.WaveformSeekbar
import app.simple.felicity.decorations.views.FavoriteButton
import app.simple.felicity.decorations.views.FelicityVisualizer
import app.simple.felicity.decorations.views.FlipPlayPauseView
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
class PlayerFaded : BasePlayerFragment() {

    private lateinit var binding: FragmentPlayerFadedWaveformBinding

    override val pager: FelicityPager
        get() = binding.pager

    override val count: TextView
        get() = binding.count

    override val play: FlipPlayPauseView
        get() = binding.play

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

    override val next: ImageButton
        get() = binding.next

    override val previous: ImageButton
        get() = binding.previous

    override val title: TextView
        get() = binding.title

    override val artist: TextView
        get() = binding.artist

    override val album: TextView
        get() = binding.album

    override val lrc: LrcLineView
        get() = binding.lrc

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentPlayerFadedWaveformBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        /**
         * Creates a new instance of [PlayerFaded] with an empty argument bundle.
         *
         * @return a fresh [PlayerFaded] fragment instance
         */
        fun newInstance(): PlayerFaded {
            val args = Bundle()
            val fragment = PlayerFaded()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "PlayerFaded"
    }
}