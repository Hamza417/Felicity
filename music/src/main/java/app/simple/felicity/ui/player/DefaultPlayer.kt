package app.simple.felicity.ui.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.decorations.pager.FelicityPager
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.utils.CoverUtils
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.utils.SongUtils

class DefaultPlayer : MediaFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
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
                val song = MediaManager.getSongAt(position)!!
                val uri = SongUtils.getArtworkUri(requireContext(), song.albumId, song.id) ?: Uri.EMPTY
                return CoverUtils.getAlbumArtBitmap(requireContext(), uri, SIZE)
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
    }

    override fun onPositionChanged(position: Int) {
        super.onPositionChanged(position)
        Log.i(TAG, "Position changed to $position")
        binding.pager.setCurrentItem(position, true)
        binding.count.text = buildString {
            append(position + 1)
            append("/")
            append(MediaManager.getSongs().size)
        }
    }

    override fun onSong(song: Song) {
        super.onSong(song)
        binding.title.text = song.title
        binding.artist.text = song.artist
        binding.album.text = song.album
        binding.info.text = song.path
        binding.duration.text = NumberUtils.getFormattedTime(song.duration)
        binding.seekbar.setMax(song.duration.toFloat())
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.seekbar.setProgress(seek.toFloat(), false)
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