package app.simple.felicity.activities

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.miniplayer.AdapterMiniPlayer
import app.simple.felicity.adapters.ui.miniplayer.AdapterMiniPlayer.Companion.MiniPlayerAdapterCallbacks
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.databinding.MiniplayerBinding
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isReadMediaAudioPermissionGranted
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.shared.storage.RemovableStorageExample
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.home.ArtFlowHome
import app.simple.felicity.ui.home.CarouselHome
import app.simple.felicity.ui.home.SimpleHome
import app.simple.felicity.ui.home.SpannedHome
import app.simple.felicity.ui.launcher.Setup
import app.simple.felicity.ui.player.DefaultPlayer
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

        RemovableStorageExample.getAllRemovableStorageExample(this)
        miniPlayerBinding = MiniplayerBinding.inflate(layoutInflater)
        binding.miniPlayer.setContent(miniPlayerBinding) { binding ->
            binding.pager.offscreenPageLimit = 1
            binding.pager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageScrollStateChanged(state: Int) {
                    super.onPageScrollStateChanged(state)
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        MediaManager.updatePosition(binding.pager.currentItem)
                    }
                }
            })
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
                if (miniPlayerBinding.pager.currentItem != position) {
                    miniPlayerBinding.pager.setCurrentItem(position, true)
                }
                generateAlbumArtPalette()
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

                adapterMiniPlayer.setCallbacks(object : MiniPlayerAdapterCallbacks {
                    override fun onOpenPlayer() {
                        // Make top fragment open player
                        // Based on app architecture, there will always be a fragment at the top
                        // and mini player won't/shouldn't be visible in player panels
                        val topFragment = supportFragmentManager.fragments.lastOrNull() as? ScopedFragment
                        if (topFragment.isNotNull()) {
                            topFragment?.openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
                        }
                    }

                    override fun onOpenPopupPlayer() {

                    }
                })
            }
        }

        lifecycleScope.launch {
            MediaManager.playbackStateFlow.collect { state ->
                when (state) {
                    MediaConstants.PLAYBACK_PLAYING -> {
                        miniPlayerBinding.playPause.setPlaying(playing = false, animate = true)
                    }
                    MediaConstants.PLAYBACK_PAUSED -> {
                        miniPlayerBinding.playPause.setPlaying(playing = true, animate = true)
                    }
                }
            }
        }
    }

    private fun setHomePanel() {
        // Check if all required permissions are granted
        val allPermissionsGranted = isReadMediaAudioPermissionGranted() &&
                isPostNotificationsPermissionGranted()

        if (!allPermissionsGranted) {
            // Show Setup screen first to request permissions
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, Setup.newInstance(), Setup.TAG)
                .commit()
        } else {
            // All permissions granted, go directly to Home
            showHome()
        }
    }

    fun showHome() {
        when (HomePreferences.getHomeInterface()) {
            HomePreferences.HOME_INTERFACE_CAROUSEL -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, CarouselHome.newInstance(), CarouselHome.TAG)
                    .commit()
            }

            HomePreferences.HOME_INTERFACE_SPANNED -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SpannedHome.newInstance(), SpannedHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_ARTFLOW -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ArtFlowHome.newInstance(), ArtFlowHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_SIMPLE -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SimpleHome.newInstance(), SimpleHome.TAG)
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
        if (supportFragmentManager.fragments.last() is MediaFragment) {
            val currentFragment = supportFragmentManager
                .fragments
                .lastOrNull { it.isVisible }

            val visible = (currentFragment as? MiniPlayerPolicy)?.wantsMiniPlayerVisible ?: true

            if (visible) {
                binding.miniPlayer.show(animated = true)
            }
        } else {
            binding.miniPlayer.show(animated = true)
        }
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