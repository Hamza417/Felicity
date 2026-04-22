package app.simple.felicity.activities

import android.app.SearchManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.crash.CrashReporter
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.databinding.DialogDeleteSongBinding
import app.simple.felicity.databinding.DialogSelectionMenuBinding
import app.simple.felicity.decorations.miniplayer.MiniPlayer
import app.simple.felicity.decorations.miniplayer.MiniPlayerItem
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.utils.PermissionUtils.isPostNotificationsPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isReadMediaAudioPermissionGranted
import app.simple.felicity.decorations.utils.PermissionUtils.isSAFAccessGranted
import app.simple.felicity.dialogs.app.VolumeKnob
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.dialogs.playlists.AddMultipleToPlaylistDialog.Companion.showAddMultipleToPlaylistDialog
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.extensions.fragments.BasePlayerFragment
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtIntoBitmap
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.managers.LyricsManager
import app.simple.felicity.preferences.TrialPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import app.simple.felicity.repository.services.AudioDatabaseService
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.shared.utils.UnitUtils.dpToPx
import app.simple.felicity.ui.home.ArtFlowHome
import app.simple.felicity.ui.home.Dashboard
import app.simple.felicity.ui.home.SimpleHome
import app.simple.felicity.ui.home.SpannedHome
import app.simple.felicity.ui.launcher.Setup
import app.simple.felicity.ui.launcher.TrialExpired
import app.simple.felicity.ui.panels.Equalizer
import app.simple.felicity.ui.panels.Selections
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.ui.player.PlayerFaded
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(), MiniPlayerCallbacks {

    private lateinit var binding: ActivityMainBinding

    /**
     * The central lyrics coordinator. Injected here so we can ask it to do a
     * re-lookup whenever the app comes back to the foreground and the current
     * song still has no lyrics — handy when the user manually dropped an .lrc
     * file into the right folder while Felicity was in the background.
     */
    @Inject
    lateinit var lyricsManager: LyricsManager

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

        if (savedInstanceState.isNull()) {
            // Cold start: push the miniplayer off-screen immediately so it is not
            // visible before the song queue and themes are fully restored.
            // onStateReady() will reveal it once everything is ready.
            binding.miniPlayer.hide(animated = false)
            setHomePanel()

            startAudioDatabaseService()
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
                binding.selectionCount.setSelectedSongsSize(selected.size)
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
            val gap = dpToPx(4f).toInt()
            val alp = binding.miniPlayerActionBar.layoutParams as? ViewGroup.MarginLayoutParams
            if (alp != null && alp.bottomMargin != totalMiniPlayerSpace + gap) {
                alp.bottomMargin = totalMiniPlayerSpace + gap
                binding.miniPlayerActionBar.requestLayout()
            }
        }

        // Wire up the three action bar buttons — delete, share, and the three-dots more menu.
        // HighlightImageButton automatically handles its own icon tinting, so no manual tint needed.
        binding.actionDelete.setOnClickListener {
            deleteSelectedSongs()
        }

        binding.actionShare.setOnClickListener {
            shareSelectedAudios(SelectionManager.selectedAudios.value)
        }

        binding.actionMore.setOnClickListener {
            showSelectionMenu()
        }

        binding.selectionCount.setOnClickListener {
            // Open the full selections panel so the user can see and manage
            // every song in the basket — much more comfortable than a tiny dialog.
            val topFragment = supportFragmentManager.fragments.lastOrNull() as? ScopedFragment
            topFragment?.openFragment(Selections.newInstance(), Selections.TAG)
        }
    }

    private fun startAudioDatabaseService() {
        // SAF access (at least one folder granted) is enough to start the scan.
        if (applicationContext.isSAFAccessGranted()) {
            AudioDatabaseService.startScan(applicationContext)
        }
    }

    private fun setHomePanel() {
        val allPermissionsGranted = isSAFAccessGranted() &&
                isPostNotificationsPermissionGranted() &&
                isReadMediaAudioPermissionGranted()

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
     * Shows the delete-confirmation dialog for all currently selected songs.
     * The dialog tells the user exactly how many songs are about to be wiped and gives them
     * a chance to back out before anything irreversible happens. If they confirm, every
     * selected song gets removed from the playback queue (or playback skips forward if one
     * is currently playing), then the physical files and database rows are deleted.
     */
    private fun deleteSelectedSongs() {
        val songsToDelete = SelectionManager.selectedAudios.value.toList()
        if (songsToDelete.isEmpty()) return

        SimpleDialog.Builder(
                container = binding.appContainer,
                inflateBinding = DialogDeleteSongBinding::inflate)
            .onViewCreated { dialogBinding ->
                // Show exactly how many songs are about to meet their maker.
                // e.g. "4 Songs will be permanently deleted from storage."
                val countLabel = getString(R.string.x_songs, songsToDelete.size)
                dialogBinding.deleteSummary.text = getString(R.string.delete_audio_summary, countLabel)
            }
            .onDialogInflated { dialogBinding, dismiss, _ ->
                dialogBinding.sure.setOnClickListener {
                    dismiss()
                    performBulkDelete(songsToDelete, deleteLyrics = dialogBinding.deleteLyricsCheckbox.isChecked)
                }
                dialogBinding.cancel.setOnClickListener { dismiss() }
            }
            .onDismiss { /* nothing to clean up after a confirmation dialog */ }
            .build()
            .show()
    }

    /**
     * Does the actual heavy lifting of deleting every song in [songs].
     * For each song we:
     *   1. Pull it out of the playback queue (or skip forward if it is playing right now).
     *   2. Delete the file via SAF using DocumentsContract — because the URI is a
     *      content:// address, not a file-system path, so File.delete() won't work.
     *   3. Remove its record from the database.
     *   4. Optionally nuke the associated internally-stored lyrics sidecar files too.
     *
     * @param songs        The batch of tracks to permanently delete.
     * @param deleteLyrics Whether to also remove any .lrc / .txt sidecar lyrics files.
     */
    private fun performBulkDelete(songs: List<Audio>, deleteLyrics: Boolean) {
        lifecycleScope.launch {
            // Step 1: Kick every song out of the queue on the main thread first, so playback
            // never tries to read a URI we are about to delete.
            songs.forEach { audio ->
                val queueIndex = MediaPlaybackManager.getSongs().indexOfFirst { it.id == audio.id }
                when {
                    queueIndex != -1 -> MediaPlaybackManager.removeQueueItemSilently(queueIndex)
                    MediaPlaybackManager.getCurrentSong()?.id == audio.id -> MediaPlaybackManager.next()
                }
            }

            // Step 2: Delete files + database rows on the IO thread.
            withContext(Dispatchers.IO) {
                val db = AudioDatabase.getInstance(applicationContext)
                songs.forEach { audio ->
                    runCatching {
                        // Use DocumentsContract.deleteDocument for SAF content URIs.
                        DocumentsContract.deleteDocument(contentResolver, audio.uri.toUri())
                        db.audioDao()?.delete(audio)

                        if (deleteLyrics) {
                            LrcRepository.deleteSidecarsStatic(applicationContext, audio.uri)
                        }
                    }.onFailure { e ->
                        Log.e("MainActivity", "Failed to delete ${audio.title}: ${e.message}", e)
                    }
                }
            }

            // Step 3: Clear the selection basket now that everything is gone.
            SelectionManager.clear()
        }
    }

    /**
     * Opens a dialog showing how many songs are selected and what actions can be performed
     * on the whole batch. The user gets to feel powerful — like a DJ with a big remote control.
     */
    private fun showSelectionMenu() {
        val selected = SelectionManager.selectedAudios.value
        SimpleDialog.Builder(
                container = binding.appContainer,
                inflateBinding = DialogSelectionMenuBinding::inflate)
            .onViewCreated { binding ->
                binding.selectionCount.setSelectedSongsSize(selected.size)
            }
            .onDialogInflated { dialogBinding, dismiss, _ ->
                dialogBinding.deleteAll.setOnClickListener {
                    dismiss()
                    // Route through the same confirmation dialog used by the action bar button.
                    deleteSelectedSongs()
                }
                dialogBinding.shareAll.setOnClickListener {
                    shareSelectedAudios(selected)
                    dismiss()
                }
                // Drop all selected songs onto the end of the current playback queue in one shot.
                dialogBinding.addAllToQueue.setOnClickListener {
                    selected.forEach { MediaPlaybackManager.addToQueue(it) }
                    SelectionManager.clear()
                    dismiss()
                }
                // Open the batch add-to-playlist dialog so the user can pick which playlists to add them to.
                dialogBinding.addToPlaylist.setOnClickListener {
                    dismiss()
                    supportFragmentManager.showAddMultipleToPlaylistDialog(selected)
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
     * Fires an Android share sheet containing all of the [songs] at once.
     * Each audio URI is already a SAF content:// address with a persisted permission grant,
     * so we pass them directly to the chooser — no FileProvider detour required.
     *
     * @param songs The tracks to share.
     */
    private fun shareSelectedAudios(songs: List<Audio>) {
        val uris = songs.map { Uri.parse(it.uri) } as ArrayList<Uri>
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
                openVolumeKnobDialog()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                openVolumeKnobDialog()
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    private fun openVolumeKnobDialog() {
        showVolumeKnob().setVolumeListener(object : VolumeKnob.Companion.VolumeListener {
            override fun onEqualizerClicked() {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, Equalizer.newInstance(), Equalizer.TAG)
                    .addToBackStack(Equalizer.TAG)
                    .commit()
            }
        })
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
        // Intentionally not starting the scan here — the scan is already kicked off
        // from onCreate() via the permission observer, and running it again on every
        // onStart() would re-trigger a full scan every time the user switches back to
        // the app from any other screen. That is the exact behavior that was causing
        // "why does the library refresh every time I resume?" — mystery solved!
    }

    override fun onResume() {
        super.onResume()
        // If the user stepped away, placed an .lrc file next to a song, and came back,
        // we want the lyrics to show up immediately without forcing a full song reload.
        // refreshIfNoLyrics() is smart enough to skip this when lyrics are already loaded.
        lyricsManager.refreshIfNoLyrics()
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

    private fun TextView.setSelectedSongsSize(size: Int) {
        text = resources.getQuantityString(R.plurals.songs_selected, size, size)
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