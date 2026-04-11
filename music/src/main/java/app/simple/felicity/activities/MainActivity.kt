package app.simple.felicity.activities

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.crash.CrashReporter
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.databinding.DialogSelectionMenuBinding
import app.simple.felicity.decorations.miniplayer.MiniPlayer
import app.simple.felicity.decorations.miniplayer.MiniPlayerItem
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.utils.PermissionUtils.isManageExternalStoragePermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.extensions.fragments.BasePlayerFragment
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtIntoBitmap
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.home.ArtFlowHome
import app.simple.felicity.ui.home.Dashboard
import app.simple.felicity.ui.home.SimpleHome
import app.simple.felicity.ui.home.SpannedHome
import app.simple.felicity.ui.launcher.Setup
import app.simple.felicity.ui.launcher.TrialExpired
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.ui.player.PlayerFaded
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

        /**
         * Initialize the crash reporter to intercept uncaught exceptions
         */
        CrashReporter(applicationContext).initialize()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.miniPlayer.callbacks = object : MiniPlayer.Callbacks {
            override fun onPageSelected(position: Int, fromUser: Boolean) {
                // Only forward to MediaPlaybackManager when the swipe originated from the
                // user.  Programmatic setCurrentItem calls (fromUser = false) must
                // be ignored; otherwise the position feedback loop causes the wrong
                // song to be played.
                if (fromUser) {
                    MediaPlaybackManager.updatePosition(position)
                }
            }

            override fun onLoadArt(position: Int, payload: Any?, setBitmap: (android.graphics.Bitmap?) -> Unit) {
                if (payload is Audio) {
                    loadArtIntoBitmap(payload, setBitmap)
                }
            }

            override fun onPlayPauseClick() {
                MediaPlaybackManager.flipState()
            }

            override fun onItemClick(position: Int) {
                val topFragment = supportFragmentManager.fragments.lastOrNull() as? ScopedFragment
                if (topFragment.isNotNull()) {
                    val player = openPlayerForCurrentPreference()
                    topFragment?.openFragment(player, BasePlayerFragment.TAG)
                }
            }
        }

        // Seek via long-press drag on the miniplayer
        binding.miniPlayer.seekListener = { fraction ->
            val duration = MediaPlaybackManager.getDuration()
            if (duration > 0L) {
                MediaPlaybackManager.seekTo((fraction * duration).toLong())
            }
        }

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            // Cold start: push the miniplayer off-screen immediately so it is not
            // visible before the song queue and themes are fully restored.
            // onStateReady() will reveal it once everything is ready.
            binding.miniPlayer.hide(animated = false)
            setHomePanel()
        }

        lifecycleScope.launch {
            MediaPlaybackManager.songSeekPositionFlow.collect { position ->
                // Skip external updates while the user is scrubbing to avoid
                // the flow fighting the touch handler and causing jitter.
                if (binding.miniPlayer.isSeeking) return@collect
                val duration = MediaPlaybackManager.getDuration()
                if (duration > 0L) {
                    // animate = false: snap directly for real-time ticks; no tween lag.
                    binding.miniPlayer.setProgress(
                            fraction = position.toFloat() / duration.toFloat(),
                            animate = false
                    )
                }
            }
        }

        lifecycleScope.launch {
            MediaPlaybackManager.songPositionFlow.collect { position ->
                val currentPagerItem = binding.miniPlayer.currentItem
                if (currentPagerItem != position) {
                    binding.miniPlayer.setCurrentItem(
                            position = position,
                            // Smooth scroll if the new position is within 5 items of the current position
                            smoothScroll = false)
                }
            }
        }

        lifecycleScope.launch {
            MediaPlaybackManager.songListFlow.collect { songs ->
                Log.d("MainActivity", "songListFlow: ${songs.size}")
                val items = songs.map { audio ->
                    MiniPlayerItem(
                            title = audio.title,
                            artist = audio.getArtists(),
                            payload = audio
                    )
                }
                binding.miniPlayer.setItems(items)
                val songPosition = MediaPlaybackManager.getCurrentSongPosition()
                binding.miniPlayer.setCurrentItem(
                        if (songPosition < songs.size) songPosition else 0,
                        smoothScroll = false
                )
                // Reset the progress bar whenever the queue is replaced
                binding.miniPlayer.setProgress(0f)
            }
        }

        lifecycleScope.launch {
            MediaPlaybackManager.playbackStateFlow.collect { state ->
                when (state) {
                    MediaConstants.PLAYBACK_PLAYING -> binding.miniPlayer.setPlaying(true)
                    MediaConstants.PLAYBACK_PAUSED -> binding.miniPlayer.setPlaying(false)
                }
            }
        }

        // Watch the selection basket — when songs pile in, show the action bar above the card.
        // When the basket empties, tuck it away again so things look clean.
        lifecycleScope.launch {
            SelectionManager.selectedAudios.collect { selected ->
                updateActionBar(selected)
            }
        }

        // Keep the action bar perfectly above the mini player by mirroring its
        // translationY every frame. This runs before every draw, so they always
        // move as one unit — no manual syncing needed anywhere else.
        binding.root.viewTreeObserver.addOnPreDrawListener {
            binding.miniPlayerActionBar.translationY = binding.miniPlayer.translationY
            true
        }

        // Reposition the action bar whenever the mini player's layout changes —
        // this handles both the initial placement AND nav-bar inset updates.
        binding.miniPlayer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val lp = binding.miniPlayer.layoutParams as? ViewGroup.MarginLayoutParams
            val totalMiniPlayerSpace = binding.miniPlayer.height + (lp?.bottomMargin ?: 0)
            val gap = dpToPx(4f)
            val alp = binding.miniPlayerActionBar.layoutParams as? ViewGroup.MarginLayoutParams
            if (alp != null && alp.bottomMargin != totalMiniPlayerSpace + gap) {
                alp.bottomMargin = totalMiniPlayerSpace + gap
                binding.miniPlayerActionBar.requestLayout()
            }
        }

        // Wire up the three action bar buttons — delete, share, and the three-dots more menu.
        // Also apply the theme's icon tint so they blend in with the rest of the app.
        applyActionBarIconTints()

        binding.actionDelete.setOnClickListener {
            SelectionManager.clear()
        }

        binding.actionShare.setOnClickListener {
            shareSelectedAudios(SelectionManager.selectedAudios.value)
        }

        binding.actionMore.setOnClickListener {
            showSelectionMenu()
        }

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? AudioDatabaseService.AudioDatabaseBinder
                audioDatabaseService = binder?.getService() ?: return
            }

            override fun onServiceDisconnected(name: ComponentName?) {
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

        when {
            !allPermissionsGranted -> {
                // Show Setup screen first to request permissions
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, Setup.newInstance(), Setup.TAG)
                    .commit()
            }
            TrialPreferences.isTrialExpired() -> {
                // Trial has expired — show the paywall screen instead of home
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, TrialExpired.newInstance(), TrialExpired.TAG)
                    .commit()
            }
            else -> {
                // All permissions granted and trial is still active, go directly to home
                showHome()
            }
        }
    }

    /**
     * Creates and returns the player fragment instance that corresponds to the
     * currently selected player interface preference.
     *
     * @return a [BasePlayerFragment] subclass instance ready to be committed
     */
    private fun openPlayerForCurrentPreference(): BasePlayerFragment {
        return when (UserInterfacePreferences.getPlayerInterface()) {
            UserInterfacePreferences.PLAYER_INTERFACE_FADED -> PlayerFaded.newInstance()
            else -> DefaultPlayer.newInstance()
        }
    }

    fun showHome() {
        when (UserInterfacePreferences.getHomeInterface()) {
            UserInterfacePreferences.HOME_INTERFACE_DASHBOARD -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, Dashboard.newInstance(), Dashboard.TAG)
                    .commit()
            }
            UserInterfacePreferences.HOME_INTERFACE_SPANNED -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SpannedHome.newInstance(), SpannedHome.TAG)
                    .commit()
            }
            UserInterfacePreferences.HOME_INTERFACE_ARTFLOW -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ArtFlowHome.newInstance(), ArtFlowHome.TAG)
                    .commit()
            }
            UserInterfacePreferences.HOME_INTERFACE_SIMPLE -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, SimpleHome.newInstance(), SimpleHome.TAG)
                    .commit()
            }
        }
    }

    private fun startAudioDatabaseService() {
        if (audioDatabaseService.isNull()) {
            val intent = Intent(this, AudioDatabaseService::class.java)
            bindService(intent, serviceConnection!!, BIND_AUTO_CREATE)
        }
    }

    private fun stopAudioDatabaseService() {
        if (audioDatabaseService.isNotNull()) {
            unbindService(serviceConnection!!)
            audioDatabaseService = null
        }
    }

    /**
     * Shows or hides the action bar above the mini player based on how many songs
     * are currently selected. When songs are selected, the bar slides into view with
     * a gentle fade. When the basket is empty, it quietly disappears.
     *
     * @param selected The current list of selected tracks.
     */
    private fun updateActionBar(selected: List<Audio>) {
        val shouldShow = selected.isNotEmpty()
        if (shouldShow && !binding.miniPlayerActionBar.isVisible) {
            binding.miniPlayerActionBar.visibility = View.VISIBLE
            binding.miniPlayerActionBar.animate().alpha(1f).setDuration(200).start()
        } else if (!shouldShow && binding.miniPlayerActionBar.isVisible) {
            binding.miniPlayerActionBar.animate().alpha(0f).setDuration(160).withEndAction {
                binding.miniPlayerActionBar.visibility = View.GONE
            }.start()
        }
    }

    /**
     * Applies the theme's regular icon color as a tint to each action bar button
     * so they always look at home regardless of which theme is active.
     * Think of it as dress-coding the buttons to match the rest of the app.
     */
    private fun applyActionBarIconTints() {
        val tint = android.content.res.ColorStateList.valueOf(
                ThemeManager.theme.iconTheme.regularIconColor)
        binding.actionDelete.imageTintList = tint
        binding.actionShare.imageTintList = tint
        binding.actionMore.imageTintList = tint
    }

    /**
     * Converts dp to pixels using the screen's display metrics. Just a handy shortcut
     * so we don't have to type out the full conversion formula everywhere.
     */
    private fun dpToPx(dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()

    /**
     * Opens a dialog showing how many songs are selected and what actions can be performed
     * on the whole batch. The user gets to feel powerful — like a DJ with a big remote control.
     */
    private fun showSelectionMenu() {
        val selected = SelectionManager.selectedAudios.value
        SimpleDialog.Builder(
                container = binding.appContainer,
                inflateBinding = DialogSelectionMenuBinding::inflate)
            .onViewCreated { dialogBinding ->
                dialogBinding.selectionCount.text = getString(
                        R.string.x_songs_selected, selected.size)
            }
            .onDialogInflated { dialogBinding, dismiss, _ ->
                dialogBinding.deleteAll.setOnClickListener {
                    // For now just clearing the selection — real deletion flow wired in later.
                    SelectionManager.clear()
                    dismiss()
                }
                dialogBinding.shareAll.setOnClickListener {
                    shareSelectedAudios(selected)
                    dismiss()
                }
                dialogBinding.clearSelection.setOnClickListener {
                    SelectionManager.clear()
                    dismiss()
                }
            }
            .onDismiss { /* nothing to clean up */ }
            .build()
            .show()
    }

    /**
     * Fires an Android share sheet containing all of the [songs] files at once.
     * Each file is added as a separate stream so the receiving app can handle all of them.
     * Any file that can't be wrapped in a FileProvider URI is silently skipped.
     *
     * @param songs The tracks to share.
     */
    private fun shareSelectedAudios(songs: List<Audio>) {
        val uris = songs.mapNotNull { audio ->
            runCatching {
                FileProvider.getUriForFile(this, "$packageName.provider", audio.file)
            }.getOrNull()
        }
        if (uris.isEmpty()) return

        val intent = ShareCompat.IntentBuilder(this)
            .setType("audio/*")
            .apply { uris.forEach { addStream(it) } }
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
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

    /**
     * Called by [BaseActivity] once the media queue and playback state have been fully
     * restored.  At this point it is safe to reveal the miniplayer without showing it
     * while the screen is still loading.
     *
     * @author Hamza417
     */
    override fun onStateReady() {
        if (MediaPlaybackManager.getSongs().isEmpty()) return
        val fragment = supportFragmentManager.fragments.lastOrNull { it.isVisible }
        val wantsVisible = (fragment as? MiniPlayerPolicy)?.wantsMiniPlayerVisible ?: true
        if (wantsVisible) {
            binding.miniPlayer.show(animated = true)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            TrialPreferences.HAS_LICENSE_KEY -> {

            }
        }
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

    override fun onAttachMiniPlayerScrollView(scrollView: NestedScrollView?) {
        scrollView?.let {
            binding.miniPlayer.attachToScrollView(it)
        }
    }

    override fun onDetachMiniPlayerScrollView(scrollView: NestedScrollView?) {
        scrollView?.let {
            binding.miniPlayer.detachFromScrollView(it)
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