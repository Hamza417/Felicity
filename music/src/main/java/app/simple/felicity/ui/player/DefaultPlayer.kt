package app.simple.felicity.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.app.SharedElementCallback
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.simple.felicity.R
import app.simple.felicity.adapters.player.DefaultPlayerAdapter
import app.simple.felicity.core.utils.NumberUtils
import app.simple.felicity.databinding.FragmentDefaultPlayerBinding
import app.simple.felicity.extensions.fragments.PlayerFragment
import app.simple.felicity.viewmodels.main.player.PlayerViewModel

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

        setEnterSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: List<String>, sharedElements: MutableMap<String, View>) {
                // Locate the ViewHolder for the clicked position.
                val selectedViewHolder = (binding.artSlider[0] as RecyclerView)
                    .findViewHolderForAdapterPosition(app.simple.felicity.preferences.MusicPreferences.getMusicPosition())
                if (selectedViewHolder is DefaultPlayerAdapter.Holder) {
                    // Map the first shared element name to the child ImageView.
                    sharedElements[names[0]] = selectedViewHolder.binding.art
                }
            }
        })

        postponeEnterTransition()

        playerViewModel.getSongs().observe(viewLifecycleOwner) { list ->
            binding.artSlider.adapter = DefaultPlayerAdapter(list)
            binding.artSlider.setCurrentItem(app.simple.felicity.preferences.MusicPreferences.getMusicPosition(), false)

            /**
             * This will break the transition for some reason, start the animation without
             * any callback
             */
            //                (view.parent as? ViewGroup)?.doOnPreDraw {
            //                    startPostponedEnterTransition()
            //                }

            /**
             * Like this, it works fine here
             */
            startPostponedEnterTransition()
            setMetaData(binding.artSlider.currentItem)

            binding.artSlider.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        if (binding.artSlider.currentItem != app.simple.felicity.preferences.MusicPreferences.getMusicPosition()) {
                            handler.removeCallbacks(progressRunnable)
                            currentSeekPosition = 0
                            app.simple.felicity.preferences.MusicPreferences.setMusicPosition(binding.artSlider.currentItem)
                            app.simple.felicity.preferences.MusicPreferences.setLastMusicId(list[binding.artSlider.currentItem].id)
                            setMetaData(binding.artSlider.currentItem)
                        }
                    }
                }
            })
        }

        binding.nextButton.setOnClickListener {
            handler.removeCallbacks(progressRunnable)
        }

        binding.previousButton.setOnClickListener {
            handler.removeCallbacks(progressRunnable)
        }

        binding.playButton.setOnClickListener {

        }

        binding.closeButton.setOnClickListener {
            stopService()
        }

        binding.seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.currentProgress.text = NumberUtils.getFormattedTime(progress.toLong())
                    currentSeekPosition = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

                binding.seekbar.clearAnimation()
                handler.removeCallbacks(progressRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                handler.post(progressRunnable)
            }
        })
    }

    override fun onServiceConnected() {

    }

    override fun onServiceDisconnected() {

    }

    override fun onPrepared() {

    }

    override fun onMetaData() {

    }

    override fun onQuitMusicService() {
        goBack()
    }

    override fun onStateChanged(isPlaying: Boolean) {
        buttonStatus(isPlaying)
    }

    override fun onNext() {
        currentSeekPosition = 0
        setMetaData(binding.artSlider.currentItem)
    }

    override fun onPrevious() {
        currentSeekPosition = 0
        setMetaData(binding.artSlider.currentItem)
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

    private fun setMetaData(position: Int) {

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
