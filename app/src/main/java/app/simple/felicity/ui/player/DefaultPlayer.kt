package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.DefaultPlayerAdapter
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.extensions.fragments.PlayerFragment
import app.simple.felicity.models.Audio
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.utils.NumberUtils
import app.simple.felicity.viewmodels.ui.PlayerViewModel

class DefaultPlayer : PlayerFragment() {

    private lateinit var binding: FragmentDefaultPlayerBinding
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDefaultPlayerBinding.inflate(inflater, container, false)
        playerViewModel = ViewModelProvider(requireActivity())[PlayerViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        playerViewModel.getSongs().observe(viewLifecycleOwner) { list ->
            audios = list
            binding.artSlider.adapter = DefaultPlayerAdapter(list)
            binding.artSlider.currentItem = MusicPreferences.getMusicPosition()
            setMetaData(list[MusicPreferences.getMusicPosition()])

            binding.artSlider.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        if (binding.artSlider.currentItem != MusicPreferences.getMusicPosition()) {
                            handler.removeCallbacks(progressRunnable)
                            currentSeekPosition = 0
                            MusicPreferences.setMusicPosition(binding.artSlider.currentItem)
                            MusicPreferences.setLastMusicId(list[binding.artSlider.currentItem].id)
                            audioService?.setCurrentPosition(binding.artSlider.currentItem)
                            setMetaData(list[MusicPreferences.getMusicPosition()])
                        }
                    }
                }
            })
        }

        binding.nextButton.setOnClickListener {
            audioService?.playNext()
        }

        binding.previousButton.setOnClickListener {
            audioService?.playPrevious()
        }

        binding.playButton.setOnClickListener {
            audioService?.changePlayerState()
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentProgress.text = NumberUtils.getFormattedTime(progress.toLong())
                    currentSeekPosition = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                if (seekBar.max != audioService?.getDuration()!!) {
                    seekBar.max = audioService?.getDuration()!!
                }

                binding.seekbar.clearAnimation()
                handler.removeCallbacks(progressRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                audioService?.seek(seekBar.progress)
                handler.post(progressRunnable)
            }
        })
    }

    override fun onServiceConnected() {

    }

    override fun onServiceDisconnected() {

    }

    override fun onPrepared() {
        audioService?.let {
            if (it.isPlaying()) {
                onStateChanged(true)
            } else {
                onStateChanged(false)
            }
        }
    }

    override fun onMetaData() {
        audioService?.let {
            binding.seekbar.max = it.getDuration()
            binding.currentDuration.text = NumberUtils.getFormattedTime(it.getDuration().toLong())
        }
    }

    override fun onQuitMusicService() {
        goBack()
    }

    override fun onStateChanged(isPlaying: Boolean) {
        buttonStatus(isPlaying)
    }

    override fun onNext() {
        currentSeekPosition = 0
        if (binding.artSlider.currentItem < audios.size - 1) {
            binding.artSlider.setCurrentItem(binding.artSlider.currentItem + 1, true)
        } else {
            binding.artSlider.setCurrentItem(0, true)
        }

        setMetaData(audios[binding.artSlider.currentItem])
    }

    override fun onPrevious() {
        currentSeekPosition = 0
        if (binding.artSlider.currentItem > 0) {
            binding.artSlider.setCurrentItem(binding.artSlider.currentItem - 1, true)
        } else {
            binding.artSlider.setCurrentItem(audios.size - 1, true)
        }

        setMetaData(audios[binding.artSlider.currentItem])
    }

    override fun onBuffering(progress: Int) {
        binding.seekbar.updateSecondaryProgress(progress)
    }

    override fun onMediaError(error: String) {
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    override fun onProgress(progress: Int, duration: Int) {
        binding.seekbar.updateProgress(progress, duration)
        binding.currentProgress.text = NumberUtils.getFormattedTime(progress.toLong())
    }

    private fun buttonStatus(isPlaying: Boolean, animate: Boolean = true) {
        if (isPlaying) {
            binding.playButton.setIcon(R.drawable.ic_pause, animate)
        } else {
            binding.playButton.setIcon(R.drawable.ic_play, animate)
        }
    }

    private fun setMetaData(audio: Audio) {
        binding.title.text = audio.title
        binding.artist.text = audio.artist
        binding.album.text = audio.album
        binding.info.text = buildString {
            append(".")
            append(audio.path.substringAfterLast("."))
            append(" | ")
            append(audio.bitrate)
            append(" | ")
            append(audio.mimeType)
        }
    }

    companion object {
        fun newInstance(): DefaultPlayer {
            val args = Bundle()
            val fragment = DefaultPlayer()
            fragment.arguments = args
            return fragment
        }
    }
}