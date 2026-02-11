package app.simple.felicity.ui.player

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.shared.utils.NumberUtils

class DefaultPlayer : MediaFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        binding.pager.setAdapter(object : FelicityPager.Adapter {
            override fun getCount(): Int {
                return MediaManager.getSongs().size
            }

            override fun loadBitmap(position: Int): Bitmap? {
                return AudioCover.load(MediaManager.getSongAt(position)!!)
            }
        })

        binding.pager.setCurrentItem(MediaManager.getCurrentPosition(), false)

        binding.pager.addOnPageChangeListener(object : FelicityPager.OnPageChangeListener {
            override fun onPageSelected(position: Int, fromUser: Boolean) {
                super.onPageSelected(position, fromUser)
                if (fromUser) {
                    MediaManager.updatePosition(position)
                }
            }
        })

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
        }

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    MediaManager.seekTo(progress.toLong())
                    binding.currentTime.text = NumberUtils.getFormattedTime(progress.toLong())
                }
            }
        })

        binding.lyrics.setOnClickListener {
            openFragment(Lyrics.newInstance(), Lyrics.TAG)
        }
    }

    override fun onPositionChanged(position: Int) {
        super.onPositionChanged(position)
        Log.i(TAG, "Position changed to $position")
        if (binding.pager.getCurrentItem() != position) {
            binding.pager.setCurrentItem(position, true)
        }
        binding.count.text = buildString {
            append(position + 1)
            append("/")
            append(MediaManager.getSongs().size)
        }
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        binding.title.text = audio.title
        binding.artist.text = audio.artist
        binding.album.text = audio.album
        binding.info.text = audio.path
        binding.duration.text = NumberUtils.getFormattedTime(audio.duration)
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.seekbar.setProgress(seek.toFloat(), false, animate = true)
        binding.currentTime.text = NumberUtils.getFormattedTime(seek)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> {
                binding.play.setImageResource(app.simple.felicity.decoration.R.drawable.ic_pause)
            }
            MediaConstants.PLAYBACK_PAUSED -> {
                binding.play.setImageResource(app.simple.felicity.decoration.R.drawable.ic_play)
            }
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    companion object {
        fun newInstance(): DefaultPlayer {
            val args = Bundle()
            val fragment = DefaultPlayer()
            fragment.arguments = args
            return fragment
        }

        private const val SIZE = 1024

        const val TAG = "DefaultPlayer"
    }
}