package app.simple.felicity.activities

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.miniplayer.AdapterMiniPlayer
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.databinding.MiniplayerBinding
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.main.home.ArtFlowHome
import app.simple.felicity.ui.main.home.CarouselHome
import app.simple.felicity.ui.main.home.SimpleHome
import app.simple.felicity.ui.main.home.SpannedHome
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity(), MiniPlayerCallbacks {

    private lateinit var binding: ActivityMainBinding
    private lateinit var miniPlayerBinding: MiniplayerBinding

    private lateinit var adapterMiniPlayer: AdapterMiniPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        miniPlayerBinding = MiniplayerBinding.inflate(layoutInflater)
        binding.miniPlayer.setContent(miniPlayerBinding) { binding ->
            binding.pager.offscreenPageLimit = 1
            binding.pager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    MediaManager.updatePosition(position)
                }
            })
            binding.next.setOnClickListener {
                MediaManager.next()
            }
            binding.previous.setOnClickListener {
                MediaManager.previous()
            }
            binding.playPause.setOnClickListener {
                MediaManager.flipState()
            }
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            setHomePanel()
        }

        lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                PlayerPreferences.setLastSongSeek(position)
            }
        }

        lifecycleScope.launch {
            MediaManager.songPositionFlow.collect { position ->
                PlayerPreferences.setLastSongPosition(position)
                miniPlayerBinding.pager.setCurrentItem(position, true)
            }
        }

        lifecycleScope.launch {
            MediaManager.songListFlow.collect { songs ->
                Log.d("MainActivity", "songListFlow: ${songs.size}")
                adapterMiniPlayer = AdapterMiniPlayer(songs)
                miniPlayerBinding.pager.adapter = adapterMiniPlayer
                val songPosition = MediaManager.getCurrentPosition()
                if (songPosition < songs.size) {
                    miniPlayerBinding.pager.setCurrentItem(songPosition, false)
                } else {
                    miniPlayerBinding.pager.setCurrentItem(0, false)
                }
            }
        }

        lifecycleScope.launch {
            MediaManager.playbackStateFlow.collect { state ->
                when (state) {
                    MediaConstants.PLAYBACK_PLAYING -> {
                        miniPlayerBinding.playPause.setImageResource(R.drawable.ic_pause)
                    }
                    MediaConstants.PLAYBACK_PAUSED -> {
                        miniPlayerBinding.playPause.setImageResource(R.drawable.ic_play)
                    }
                }
            }
        }
    }

    private fun setHomePanel() {
        when (HomePreferences.getHomeInterface()) {
            HomePreferences.HOME_INTERFACE_CAROUSEL -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, CarouselHome.newInstance(), CarouselHome.TAG)
                    .commit()
            }

            HomePreferences.HOME_INTERFACE_SPANNED -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SpannedHome.newInstance(), SpannedHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_ARTFLOW -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, ArtFlowHome.newInstance(), ArtFlowHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_SIMPLE -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SimpleHome.newInstance(), SimpleHome.TAG)
                    .commit()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                showVolumeKnob()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                showVolumeKnob()
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onHideMiniPlayer() {
        binding.miniPlayer.hide(animated = true)
    }

    override fun onShowMiniPlayer() {
        binding.miniPlayer.show(animated = true)
    }

    override fun onAttachMiniPlayer(recyclerView: RecyclerView?) {
        recyclerView?.let {
            binding.miniPlayer.attachToRecyclerView(it)
        }
    }

    override fun onDetachMiniPlayer(recyclerView: RecyclerView?) {
        recyclerView?.let {
            binding.miniPlayer.detachFromRecyclerView(it)
        }
    }
}