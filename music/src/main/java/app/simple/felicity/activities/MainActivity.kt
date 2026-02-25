package app.simple.felicity.activities

import android.app.SearchManager
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.decorations.utils.PermissionUtils.isManageExternalStoragePermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.decorations.views.MiniPlayer
import app.simple.felicity.decorations.views.MiniPlayerItem
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadPeristyleArtCover
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.home.ArtFlowHome
import app.simple.felicity.ui.home.SimpleHome
import app.simple.felicity.ui.home.SpannedHome
import app.simple.felicity.ui.launcher.Setup
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.setup.PermissionViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity(), MiniPlayerCallbacks {

    private lateinit var binding: ActivityMainBinding

    private var serviceConnection: ServiceConnection? = null
    private var audioDatabaseService: AudioDatabaseService? = null

    private val permissionViewModel by viewModels<PermissionViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.miniPlayer.callbacks = object : MiniPlayer.Callbacks {
            override fun onPageSelected(position: Int) {
                MediaManager.updatePosition(position)
            }

            override fun onLoadArt(imageView: ImageView, payload: Any?) {
                if (payload is Audio) {
                    imageView.loadPeristyleArtCover(payload)
                }
            }

            override fun onPlayPauseClick() {
                MediaManager.flipState()
            }

            override fun onItemClick(position: Int) {
                val topFragment = supportFragmentManager.fragments.lastOrNull() as? ScopedFragment
                if (topFragment.isNotNull()) {
                    topFragment?.openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
                }
            }
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            setHomePanel()
        }

        lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->

            }
        }

        lifecycleScope.launch {
            MediaManager.songPositionFlow.collect { position ->
                val currentPagerItem = binding.miniPlayer.currentItem
                if (currentPagerItem != position) {
                    binding.miniPlayer.setCurrentItem(position, smoothScroll = true)
                }
            }
        }

        lifecycleScope.launch {
            MediaManager.songListFlow.collect { songs ->
                Log.d("MainActivity", "songListFlow: ${songs.size}")
                val items = songs.map { audio ->
                    MiniPlayerItem(
                            title = audio.title ?: audio.name ?: "",
                            artist = audio.artist ?: "",
                            payload = audio
                    )
                }
                binding.miniPlayer.setItems(items)
                val songPosition = MediaManager.getCurrentPosition()
                binding.miniPlayer.setCurrentItem(
                        if (songPosition < songs.size) songPosition else 0,
                        smoothScroll = false
                )
            }
        }

        lifecycleScope.launch {
            MediaManager.playbackStateFlow.collect { state ->
                when (state) {
                    MediaConstants.PLAYBACK_PLAYING -> binding.miniPlayer.setPlaying(true)
                    MediaConstants.PLAYBACK_PAUSED -> binding.miniPlayer.setPlaying(false)
                }
            }
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as? AudioDatabaseService.AudioDatabaseBinder
                audioDatabaseService = binder?.getService() ?: return
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                audioDatabaseService = null
            }
        }

        permissionViewModel.getManageFilesPermissionState().observe(this) { granted ->
            if (granted) {
                audioDatabaseService?.refreshAudioFiles()
            }
        }
    }

    private fun setHomePanel() {
        // Check if all required permissions are granted
        val allPermissionsGranted = isManageExternalStoragePermissionGranted() &&
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

    private fun startAudioDatabaseService() {
        if (audioDatabaseService.isNull()) {
            val intent = android.content.Intent(this, AudioDatabaseService::class.java)
            bindService(intent, serviceConnection!!, BIND_AUTO_CREATE)
        }
    }

    private fun stopAudioDatabaseService() {
        if (audioDatabaseService.isNotNull()) {
            unbindService(serviceConnection!!)
            audioDatabaseService = null
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
        Log.d("MainActivity", "Detaching mini player from RecyclerView")
        recyclerView?.let {
            binding.miniPlayer.detachFromRecyclerView(it)
        }
    }

    override fun onMakeTransparentMiniPlayer() {
        binding.miniPlayer.makeTransparent(animated = true)
    }

    override fun onMakeOpaqueMiniPlayer() {
        binding.miniPlayer.makeOpaque(animated = true)
    }

    override fun onStart() {
        super.onStart()
        startAudioDatabaseService()
    }

    override fun onResume() {
        super.onResume()
        AudioDatabaseService.refreshScan(applicationContext)
    }

    override fun onStop() {
        super.onStop()
        savePlaybackState()
    }

    private fun savePlaybackState() {
        lifecycleScope.launch(Dispatchers.IO) {
            PlaybackStateManager.saveCurrentPlaybackState(applicationContext, "MainActivity")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSearchIntent(intent)
    }

    private fun handleSearchIntent(intent: Intent) {
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY)
            Log.d("MainActivity", "Search query: $query")
        } else {
            Log.d("MainActivity", "Received non-search intent: ${intent.action}")
        }
    }
}