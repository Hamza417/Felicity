package app.simple.felicity.extensions.fragments

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.core.maths.Number.toNegative
import app.simple.felicity.databinding.DialogDeleteSongBinding
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.databinding.DialogSureBinding
import app.simple.felicity.decorations.popups.SharedImageDialogMenu
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.popups.SimpleSharedImageDialog
import app.simple.felicity.dialogs.app.AudioInformation.Companion.showAudioInfo
import app.simple.felicity.dialogs.lyrics.Lyrics.Companion.showLyrics
import app.simple.felicity.dialogs.playlists.AddToPlaylistDialog.Companion.showAddToPlaylistDialog
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.PlaybackStateManager
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.preferences.ShufflePreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.shuffle.Shuffle.shuffle
import app.simple.felicity.shared.utils.BarHeight
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.shared.utils.ViewUtils.cancelTouch
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.panels.Milkdrop
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.ui.player.PlayerFaded
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

open class MediaFragment : ScopedFragment(), MiniPlayerPolicy {

    private var shouldShowMiniPlayer = true
    private var lastSavedSeekPosition = 0L

    private val miniPlayerCallbacks: MiniPlayerCallbacks?
        get() = requireActivity() as? MiniPlayerCallbacks

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.songSeekPositionFlow.collect { position ->
                onSeekChanged(position)

                // Save to database every 5 seconds or 5% of duration, whichever is larger
                val song = MediaPlaybackManager.getCurrentSong()
                if (song != null) {
                    val threshold = maxOf(5000L, song.duration / 20) // 5 seconds or 5% of duration
                    if (abs(position - lastSavedSeekPosition) > threshold) {
                        lastSavedSeekPosition = position
                        saveCurrentPlaybackState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            // repeatOnLifecycle(STARTED) ensures we re-subscribe and replay the last
            // emitted position every time the fragment comes back to the foreground.
            // This guarantees onAudio() fires on resume, clearing any stale highlights.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MediaPlaybackManager.songPositionFlow.collect { position ->
                    Log.d(TAG, "Song position: $position")
                    MediaPlaybackManager.getCurrentSong()?.let { song ->
                        onAudio(song)
                    }
                    onPositionChanged(position)
                    // Save state when song position changes
                    saveCurrentPlaybackState()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.songListFlow.collect { songs ->
                Log.d(TAG, "Song list updated: ${songs.size} songs")
                onSongListChanged(songs)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaPlaybackManager.playbackStateFlow.collect { state ->
                onPlaybackStateChanged(state)
            }
        }
    }

    protected fun setMediaItems(songs: List<Audio>, position: Int = 0) {
        val currentSong = MediaPlaybackManager.getCurrentSong()
        val requestedSong = songs.getOrNull(position)
        val isSameQueue = MediaPlaybackManager.isSameQueue(songs)
        val isSameSong = currentSong != null && requestedSong != null && currentSong.id == requestedSong.id

        when {
            isSameQueue && isSameSong -> {
                // Case 1: Same queue, same song — just open the player
                openDefaultPlayer().also {
                    /**
                     * User tapped the same song that's already playing or paused in the queue.
                     * Open the player without changing anything, but if the song was paused,
                     * resume playback since the user explicitly tapped it.
                     */
                    MediaPlaybackManager.startPlayingIfPaused()
                }
            }
            isSameQueue && !isSameSong -> {
                // Case 4: Same queue but different song — user tapped explicitly, always play.
                MediaPlaybackManager.updatePosition(position, forcePlay = true)
            }
            isSameSong -> {
                // Case 2: Same song playing but queue is different — update queue silently and open player
                updateQueueSilently(songs, position)
            }
            else -> {
                // Case 3: Different queue and different song — default behavior
                MediaPlaybackManager.setSongs(songs, position, autoPlay = true)
                createSongHistoryDatabase(songs)
            }
        }

        /**
         * Show miniplayer in all cases when setting media items, because if the user is explicitly tapping
         * to play a song, they likely want quick access to playback controls. This also ensures the miniplayer
         * is visible when navigating to the player from a different screen (e.g. from the playing queue or from a notification)
         */
        showMiniPlayer()
    }

    /**
     * Shuffle [songs] using the algorithm from [ShufflePreferences], then always start
     * playing from position 0 of the shuffled list. The shuffled queue replaces the current
     * queue entirely so the player always starts fresh from the first shuffled song.
     */
    protected fun shuffleMediaItems(songs: List<Audio>) {
        val algorithm = ShufflePreferences.getShuffleAlgorithm()
        val shuffled = songs.shuffle(algorithm).toMutableList()
        // Always replace queue and start from position 0, regardless of what is currently playing.
        MediaPlaybackManager.setSongs(shuffled, 0, autoPlay = true)
        createSongHistoryDatabase(shuffled)
    }

    private fun openDefaultPlayer() {
        val player = when (UserInterfacePreferences.getPlayerInterface()) {
            UserInterfacePreferences.PLAYER_INTERFACE_FADED -> PlayerFaded.newInstance()
            else -> DefaultPlayer.newInstance()
        }
        openFragment(player, BasePlayerFragment.TAG)
    }

    private fun updateQueueSilently(songs: List<Audio>, position: Int) {
        MediaPlaybackManager.updateQueueSilently(songs, position)
        createSongHistoryDatabase(songs)
    }

    private fun createSongHistoryDatabase(songs: List<Audio>) {
        val seek = MediaPlaybackManager.getSeekPosition()
        val idx = MediaPlaybackManager.getCurrentSongPosition()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val audioDatabase = AudioDatabase.getInstance(requireContext())
            PlaybackStateManager.savePlaybackState(
                    db = audioDatabase,
                    queueHash = songs.map { it.hash },
                    index = idx,
                    position = seek,
                    shuffle = false,
                    repeat = 0
            )
        }
    }

    private fun saveCurrentPlaybackState() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PlaybackStateManager.saveCurrentPlaybackState(requireContext(), TAG)
        }
    }

    protected fun requireHiddenMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                shouldShowMiniPlayer = false
                hideMiniPlayer()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                // Don't force-show during configuration changes; preserve current state
                if (requireActivity().isChangingConfigurations.not()) {
                    shouldShowMiniPlayer = true
                    showMiniPlayer()
                }
            }
        })
    }

    protected fun peekMiniPlayer() {
        // show mini player briefly then hide it again
        showMiniPlayer()
        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000) // Show for 2 seconds

            if (wantsMiniPlayerVisible.not()) {
                hideMiniPlayer()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun ImageButton.setupFastForwardButton() {
        var isPulsing = false

        val pulseRunnable = object : Runnable {
            override fun run() {
                val currentPosition = MediaPlaybackManager.getSeekPosition()
                val totalDuration = MediaPlaybackManager.getDuration()

                // Check if the next pulse will push us past the end of the song
                if (currentPosition + SEEK_PER_PULSE_MS >= totalDuration) {
                    // Stop the pulse
                    isPulsing = false
                    handler.removeCallbacks(this)
                    cancelTouch()
                    return
                }

                // Otherwise, continue pulsing as normal
                isPulsing = true
                MediaPlaybackManager.seekRelative(SEEK_PER_PULSE_MS)
                handler.postDelayed(this, SEEK_INTERVAL_MS)
            }
        }

        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPulsing = false
                    // Wait for the standard long-press timeout before starting the pulses
                    handler.postDelayed(pulseRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(pulseRunnable)

                    // If the user lifted their finger before it started pulsing,
                    // treat it as a standard click.
                    if (!isPulsing) {
                        view.performClick()
                        MediaPlaybackManager.next()
                    }

                    isPulsing = false
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_MOVE -> {
                    // Catches cases where the finger slides off the button
                    handler.removeCallbacks(pulseRunnable)
                    isPulsing = true
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    protected fun ImageButton.setupFastRewindButton() {
        var isPulsing = false

        val pulseRunnable = object : Runnable {
            override fun run() {
                val currentPosition = MediaPlaybackManager.getSeekPosition()

                // Check if the next pulse will push us before the start of the song
                if (currentPosition + SEEK_PER_PULSE_MS.toNegative() <= 0) {
                    // Stop the pulse
                    isPulsing = false
                    handler.removeCallbacks(this)
                    cancelTouch()
                    return
                }

                // Otherwise, continue pulsing as normal
                isPulsing = true
                MediaPlaybackManager.seekRelative(SEEK_PER_PULSE_MS.toNegative())
                handler.postDelayed(this, SEEK_INTERVAL_MS)
            }
        }

        setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPulsing = false
                    // Wait for the standard long-press timeout before starting the pulses
                    handler.postDelayed(pulseRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(pulseRunnable)

                    // If the user lifted their finger before it started pulsing,
                    // treat it as a standard click.
                    if (!isPulsing) {
                        view.performClick()
                        MediaPlaybackManager.previous()
                    }

                    isPulsing = false
                    true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_MOVE -> {
                    // Catches cases where the finger slides off the button
                    handler.removeCallbacks(pulseRunnable)
                    isPulsing = true
                    true
                }
                else -> false
            }
        }
    }

    protected fun RecyclerView.requireAttachedMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                miniPlayerCallbacks?.onAttachMiniPlayer(this@requireAttachedMiniPlayer)
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                miniPlayerCallbacks?.onDetachMiniPlayer(this@requireAttachedMiniPlayer)
            }
        })
    }

    /**
     * Attaches the mini player auto-hide behavior to this [NestedScrollView] for the
     * lifetime of the current fragment view. When the fragment starts the mini player
     * begins tracking scroll events; when the fragment pauses the listener is removed.
     *
     * Call this in [onViewCreated] on the root [NestedScrollView] of your layout whenever
     * you want the same hide-on-scroll behavior that RecyclerView-based screens use.
     */
    protected fun NestedScrollView.requireAttachedMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                miniPlayerCallbacks?.onAttachMiniPlayerScrollView(this@requireAttachedMiniPlayer)
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                miniPlayerCallbacks?.onDetachMiniPlayerScrollView(this@requireAttachedMiniPlayer)
            }
        })
    }

    protected fun showMiniPlayer() {
        miniPlayerCallbacks?.onShowMiniPlayer()
    }

    protected fun hideMiniPlayer() {
        miniPlayerCallbacks?.onHideMiniPlayer()
    }

    /**
     * Request that the mini player renders with a transparent background for the
     * duration of this fragment's lifecycle.  Useful for panels that have a dark
     * or image-based background where an opaque card would look out of place
     * (e.g. ArtFlowHome).
     */
    protected fun requireTransparentMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                miniPlayerCallbacks?.onMakeTransparentMiniPlayer()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                super.onDestroy(owner)
                if (requireActivity().isChangingConfigurations.not()) {
                    miniPlayerCallbacks?.onMakeOpaqueMiniPlayer()
                }
            }
        })
    }

    open fun onPlaybackStateChanged(state: Int) {
        Log.d(TAG, "Playback state changed: $state")
    }

    open fun onAudio(audio: Audio) {
        Log.d(TAG, "New song played: ${audio.title} by ${audio.artist}")
    }

    open fun onPositionChanged(position: Int) {
        Log.d(TAG, "Position changed: $position")
    }

    open fun onSeekChanged(seek: Long) {
        /* no-op */
    }

    /**
     * Called whenever the MediaPlaybackManager queue list changes (songs added, removed, reordered).
     * Subclasses that display the queue or its size should override this to refresh their UI.
     *
     * @param songs the updated, authoritative list of queued [Audio] tracks.
     */
    open fun onSongListChanged(songs: List<Audio>) {
        /* no-op */
    }

    protected fun openSongsMenu(
            audios: List<Audio>,
            position: Int,
            imageView: ImageView?,
            onDismiss: (() -> Unit)? = null) {

        val audio = audios[position]

        val onViewCreated: (DialogSongMenuBinding) -> Unit = { binding ->
            miniPlayerCallbacks?.onHideMiniPlayer()
            binding.title.text = audio.title
            binding.title.addAudioQualityIcon(audio)
            binding.secondaryDetail.text = audio.artist
            binding.tertiaryDetail.text = audio.album

            if (imageView.isNull()) {
                binding.cover.loadArtCoverWithPayload(audio)
            }

            val isCurrentlyPlaying = MediaPlaybackManager.getCurrentSong()?.id == audio.id

            if (isCurrentlyPlaying) {
                binding.play.gone(animate = false)
                binding.insertAndPlay.gone(animate = false)
                binding.addToQueue.gone(animate = false)
                binding.playNext.gone(animate = false)
            }

            if (audio.artist.isNullOrBlank()) binding.goToArtist.gone(animate = false)
            if (audio.album.isNullOrBlank()) binding.goToAlbum.gone(animate = false)
            if (audio.isFavorite) binding.addToFavorites.text = getString(R.string.remove_from_favorites)
            if (audio.isAlwaysSkip) binding.alwaysSkip.text = getString(R.string.never_skip)
        }

        val onDialogInflated: (DialogSongMenuBinding, () -> Unit) -> Unit = { binding, dismiss ->
            binding.play.setOnClickListener {
                val pos = audios.indexOfFirst { it.id == audio.id }.coerceAtLeast(0)
                setMediaItems(audios, pos)
                dismiss()
            }

            binding.addToQueue.setOnClickListener {
                MediaPlaybackManager.addToQueue(audio)
                dismiss()
            }

            binding.playNext.setOnClickListener {
                MediaPlaybackManager.playNext(audio)
                dismiss()
            }

            binding.addToPlaylist.setOnClickListener {
                parentFragmentManager.showAddToPlaylistDialog(audio)
                dismiss()
            }

            binding.goToArtist.setOnClickListener {
                val artistName = audio.artist ?: return@setOnClickListener
                val artist = Artist(
                        id = artistName.hashCode().toLong(),
                        name = artistName,
                        albumCount = 0,
                        trackCount = 0
                )
                openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                dismiss()
            }

            binding.goToAlbum.setOnClickListener {
                val albumName = audio.album ?: return@setOnClickListener
                val artistName = audio.artist ?: ""
                val album = Album(
                        id = audio.albumId,
                        name = albumName,
                        artist = artistName,
                        artistId = artistName.hashCode().toLong()
                )
                openFragment(AlbumPage.newInstance(album), AlbumPage.TAG)
                dismiss()
            }

            binding.addToFavorites.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val newFav = !audio.isFavorite
                    AudioDatabase.getInstance(requireContext()).audioDao()?.setFavorite(audio.id, newFav)
                    audio.isFavorite = newFav
                    if (MediaPlaybackManager.getCurrentSong()?.id == audio.id) {
                        withContext(Dispatchers.Main) { MediaPlaybackManager.notifyCurrentSongUpdated() }
                    }
                }
                dismiss()
            }

            binding.alwaysSkip.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val newSkip = !audio.isAlwaysSkip
                    AudioDatabase.getInstance(requireContext()).audioDao()?.setAlwaysSkip(audio.id, newSkip)
                    audio.setAlwaysSkip(newSkip)
                    if (newSkip && MediaPlaybackManager.getCurrentSong()?.id == audio.id) {
                        withContext(Dispatchers.Main) { MediaPlaybackManager.next() }
                    }
                }
                dismiss()
            }

            binding.share.setOnClickListener {
                val file = audio.file
                val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        file
                )
                ShareCompat.IntentBuilder(requireContext())
                    .setType("audio/*")
                    .setText(audio.title)
                    .setStream(uri)
                    .startChooser()
                dismiss()
            }

            binding.delete.setOnClickListener {
                dismiss()
                showAudioDeleteConfirmation(audio) { confirmed, lyrics ->
                    if (confirmed) {
                        deleteSong(audio, lyrics)
                    } else {
                        openSongsMenu(audios, position, imageView)
                    }
                }
            }

            binding.lyrics.setOnClickListener {
                childFragmentManager.showLyrics(audio).also { dismiss() }
            }

            binding.info.setOnClickListener {
                childFragmentManager.showAudioInfo(audio).also { dismiss() }
            }
        }

        val onDismissCallback: () -> Unit = {
            miniPlayerCallbacks?.onShowMiniPlayer()
            onDismiss?.invoke()
        }

        val widthRatio = if (BarHeight.isLandscape(requireContext())) 0.5F else SharedImageDialogMenu.DEFAULT_WIDTH_RATIO

        if (imageView != null) {
            SimpleSharedImageDialog.Builder(
                    container = requireContainerView(),
                    sourceImageView = imageView,
                    inflateBinding = DialogSongMenuBinding::inflate,
                    targetImageViewProvider = { it.cover })
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(widthRatio)
                .build()
                .show()
        } else {
            SimpleDialog.Builder(
                    container = requireContainerView(),
                    inflateBinding = DialogSongMenuBinding::inflate)
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(widthRatio)
                .build()
                .show()
        }
    }

    /**
     * Toggles the favorite state of the currently playing song.
     * Updates the [AudioDatabase] and the in-memory [Audio] object, then re-emits
     * [MediaPlaybackManager.notifyCurrentSongUpdated] so observers (e.g. [DefaultPlayer]) refresh their UI.
     */
    protected fun toggleFavorite() {
        val audio = MediaPlaybackManager.getCurrentSong() ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val newFavorite = !audio.isFavorite
            AudioDatabase.getInstance(requireContext()).audioDao()?.setFavorite(audio.id, newFavorite)
            audio.isFavorite = newFavorite
            withContext(Dispatchers.Main) {
                MediaPlaybackManager.notifyCurrentSongUpdated()
            }
        }
    }

    protected fun showAudioDeleteConfirmation(audio: Audio, onResult: (Boolean, Boolean) -> Unit) {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogDeleteSongBinding::inflate)
            .onViewCreated { binding ->
                // Duck audio if same song is playing
                if (MediaPlaybackManager.getCurrentSong()?.id == audio.id) {
                    MediaPlaybackManager.duck()
                }

                val title = audio.title
                val fullText = getString(R.string.delete_audio_summary, title)

                val startIndex = fullText.indexOf(title ?: "")
                val spannable = SpannableString(fullText)

                if (startIndex >= 0) {
                    val endIndex = startIndex + title!!.length

                    spannable.setSpan(
                            StyleSpan(Typeface.BOLD),
                            startIndex,
                            endIndex,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    val color = ThemeManager.theme.textViewTheme.primaryTextColor
                    spannable.setSpan(
                            ForegroundColorSpan(color),
                            startIndex,
                            endIndex,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                binding.deleteSummary.text = spannable
            }.onDialogInflated { binding, dismiss ->
                binding.sure.setOnClickListener {
                    onResult(true, binding.deleteLyricsCheckbox.isChecked)
                    dismiss()
                }

                binding.cancel.setOnClickListener {
                    onResult(false, false)
                    dismiss()
                }
            }
            .onDismiss {
                MediaPlaybackManager.unduck() // Ensure we unduck if user dismisses by tapping outside or pressing back
            }
            .build()
            .show()
    }

    protected fun withSureDialog(onResult: (Boolean) -> Unit) {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogSureBinding::inflate)
            .onViewCreated { binding ->
                /* no-op */
            }.onDialogInflated { binding, dismiss ->
                binding.sure.setOnClickListener {
                    onResult(true)
                    dismiss()
                }

                binding.cancel.setOnClickListener {
                    onResult(false)
                    dismiss()
                }
            }
            .onDismiss {
                /* no-op */
            }
            .build()
            .show()
    }

    private fun deleteSong(audio: Audio, lyrics: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: Skip to the next song (or remove from queue) BEFORE touching the
                // file, so playback continues smoothly without ever trying to read a deleted path.
                withContext(Dispatchers.Main) {
                    val queueIndex = MediaPlaybackManager.getSongs().indexOfFirst { it.id == audio.id }
                    when {
                        queueIndex != -1 -> {
                            // removeQueueItemSilently advances playback if this song is current.
                            MediaPlaybackManager.removeQueueItemSilently(queueIndex)
                        }
                        MediaPlaybackManager.getCurrentSong()?.id == audio.id -> {
                            // Song is playing but is not in the tracked queue list — just skip.
                            MediaPlaybackManager.next()
                        }
                    }
                }

                // Step 2: Delete the physical file.
                val file = File(audio.path)
                val deleted = if (file.exists()) {
                    file.delete()
                } else {
                    Log.w(TAG, "File does not exist: ${audio.path}")
                    true // Consider it deleted if it doesn't exist.
                }

                if (deleted) {
                    // Step 3: Remove from the database.
                    val audioDatabase = AudioDatabase.getInstance(requireContext())
                    audioDatabase.audioDao()?.delete(audio)

                    Log.d(TAG, "Song deleted successfully: ${audio.title}")

                    if (lyrics) {
                        // Also delete associated lyrics file if it exists.
                        val lyricsFile = File(audio.path.substringBeforeLast('.'), "${audio.title}.txt")
                        val lrcFile = File(audio.path.substringBeforeLast('.'), "${audio.title}.lrc")

                        if (lyricsFile.exists()) {
                            val lyricsDeleted = lyricsFile.delete()
                            if (lyricsDeleted) {
                                Log.d(TAG, "Associated lyrics file deleted: ${lyricsFile.absolutePath}")
                            } else {
                                Log.e(TAG, "Failed to delete associated lyrics file: ${lyricsFile.absolutePath}")
                            }
                        }

                        if (lrcFile.exists()) {
                            val lrcDeleted = lrcFile.delete()
                            if (lrcDeleted) {
                                Log.d(TAG, "Associated LRC file deleted: ${lrcFile.absolutePath}")
                            } else {
                                Log.e(TAG, "Failed to delete associated LRC file: ${lrcFile.absolutePath}")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Failed to delete file: ${audio.path}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting song: ${e.message}", e)
            }
        }
    }

    fun openMilkdropPanel() {
        openFragment(Milkdrop.newInstance(), Milkdrop.TAG)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = shouldShowMiniPlayer

    /**
     * Re-asserts the correct mini player visibility for this fragment when a predictive back
     * gesture is canceled. This is necessary because the previous fragment's lifecycle may
     * partially advance while the gesture is in progress (for example, via
     * {@code repeatOnLifecycle(STARTED)}), which can imperatively call {@code showMiniPlayer()}
     * and leave the shared mini player in a leaked state. Calling this on cancel ensures the
     * mini player reflects what this fragment actually wants, since the fragment remains the
     * current visible screen after the gesture is abandoned.
     */
    override fun onCancelPredictiveBack() {
        super.onCancelPredictiveBack()
        if (shouldShowMiniPlayer) {
            showMiniPlayer()
        } else {
            hideMiniPlayer()
        }
    }

    companion object {
        private const val TAG = "MediaFragment"

        private const val SEEK_PER_PULSE_MS = 2500L
        private const val SEEK_INTERVAL_MS = 500L
    }
}