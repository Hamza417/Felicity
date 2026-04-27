package app.simple.felicity.extensions.fragments

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ShareCompat
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.DialogAlbumMenuBinding
import app.simple.felicity.databinding.DialogArtistMenuBinding
import app.simple.felicity.databinding.DialogDeleteSongBinding
import app.simple.felicity.databinding.DialogEditPlaylistBinding
import app.simple.felicity.databinding.DialogPlaylistMenuBinding
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.databinding.DialogSureBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.popups.SimpleSharedImageDialog
import app.simple.felicity.decorations.utils.TextViewUtils.setStartDrawable
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
import app.simple.felicity.repository.managers.SelectionManager
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.PlaylistWithSongs
import app.simple.felicity.repository.repositories.LrcRepository
import app.simple.felicity.repository.shuffle.Shuffle.shuffle
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.panels.Milkdrop
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.ui.player.PlayerFaded
import app.simple.felicity.ui.subpanels.MetadataEditor
import app.simple.felicity.utils.AdapterUtils.addAudioQualityIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        // If even one song is in the basket already, this tap is a selection action, not a play
        // action. Toggle the tapped song in or out of the basket and call it a day.
        if (SelectionManager.hasSelection) {
            val audio = songs.getOrNull(position) ?: return
            SelectionManager.toggle(audio)
            return
        }

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
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
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

            // Flip the selection button text depending on whether this song is already in the basket.
            // It's like a toggle switch — on means "remove", off means "add".
            if (SelectionManager.isSelected(audio)) {
                binding.addToSelection.text = getString(R.string.remove_from_selection)
                binding.addToSelection.setCompoundDrawablesWithIntrinsicBounds(
                        AppCompatResources.getDrawable(requireContext(), R.drawable.ic_cross_16dp),
                        null, null, null)
            }

            // --------------- Favorites icon logic ---------------
            if (UserInterfacePreferences.isLikeIconInsteadOfThumb()) {
                if (audio.isFavorite) {
                    binding.addToFavorites.setStartDrawable(R.drawable.ic_thumb_up_16dp)
                } else {
                    binding.addToFavorites.setStartDrawable(R.drawable.ic_thumb_up_off_16dp)
                }
            } else {
                if (audio.isFavorite) {
                    binding.addToFavorites.setStartDrawable(R.drawable.ic_favorite_filled_16dp)
                } else {
                    binding.addToFavorites.setStartDrawable(R.drawable.ic_favorite_border_16dp)
                }
            }
        }

        val onDialogInflated: (DialogSongMenuBinding, () -> Unit, () -> Unit) -> Unit = { binding, dismiss, dismissImmediately ->
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

            binding.insertAndPlay.setOnClickListener {
                MediaPlaybackManager.insertAndPlay(audio)
                openDefaultPlayer()
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
                dismissImmediately()
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
                dismissImmediately()
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
                // The audio URI is already a content:// URI with a persisted SAF grant,
                // so we can pass it directly to the share sheet. No FileProvider detour needed.
                val audioUri = audio.uri.toUri()
                ShareCompat.IntentBuilder(requireContext())
                    .setType(audio.mimeType ?: "audio/*")
                    .setStream(audioUri)
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

            binding.editMetadata.setOnClickListener {
                openFragment(MetadataEditor.newInstance(audio), MetadataEditor.TAG)
                dismissImmediately()
            }

            // Drop this song into the selection basket (or pull it out) depending on its
            // current state. One button, two jobs — efficient and intuitive.
            binding.addToSelection.setOnClickListener {
                SelectionManager.toggle(audio)
                dismiss()
            }
        }

        val onDismissCallback: () -> Unit = {
            miniPlayerCallbacks?.onShowMiniPlayer()
            onDismiss?.invoke()
        }

        if (imageView != null) {
            SimpleSharedImageDialog.Builder(
                    container = requireContainerView(),
                    sourceImageView = imageView,
                    inflateBinding = DialogSongMenuBinding::inflate,
                    targetImageViewProvider = { it.cover })
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        } else {
            SimpleDialog.Builder(
                    container = requireContainerView(),
                    inflateBinding = DialogSongMenuBinding::inflate)
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
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
            }.onDialogInflated { binding, dismiss, _ ->
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

    /**
     * Shows a simple "Are you sure?" confirmation dialog with "Sure" and "Cancel" options.
     * The [onResult] callback returns true if the user confirmed, false if they canceled.
     *
     * Calls on result on both conditions, so make sure to check for the result in the block.
     */
    protected fun withSureDialog(onResult: (Boolean) -> Unit) {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogSureBinding::inflate)
            .onViewCreated { _ ->
                /* no-op */
            }.onDialogInflated { binding, dismiss, _ ->
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
                // Step 1: Remove the song from the playback queue on the main thread so playback
                // never tries to open a URI we are about to delete.
                withContext(Dispatchers.Main) {
                    val queueIndex = MediaPlaybackManager.getSongs().indexOfFirst { it.id == audio.id }
                    when {
                        queueIndex != -1 -> MediaPlaybackManager.removeQueueItemSilently(queueIndex)
                        MediaPlaybackManager.getCurrentSong()?.id == audio.id -> MediaPlaybackManager.next()
                    }
                }

                // Step 2: Delete the audio file itself. Audio is stored as a SAF content URI,
                // so we use DocumentsContract.deleteDocument instead of File.delete().
                val audioUri = audio.uri.toUri()
                val deleted = try {
                    DocumentsContract.deleteDocument(requireContext().contentResolver, audioUri)
                } catch (e: Exception) {
                    Log.w(TAG, "DocumentsContract.deleteDocument failed: ${e.message}", e)
                    false
                }

                if (deleted) {
                    // Step 3: Remove the row from the database.
                    AudioDatabase.getInstance(requireContext()).audioDao()?.delete(audio)
                    Log.d(TAG, "Song deleted successfully: ${audio.title}")

                    if (lyrics) {
                        // Clean up the internally-stored LRC/TXT sidecar files.
                        LrcRepository.deleteSidecarsStatic(requireContext(), audio.uri)
                    }
                } else {
                    Log.e(TAG, "DocumentsContract could not delete: ${audio.uri}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting song: ${e.message}", e)
            }
        }
    }

    fun openMilkdropPanel() {
        openFragment(Milkdrop.newInstance(), Milkdrop.TAG)
    }

    /**
     * Opens the playlist context menu dialog for the given [item], mirroring the pattern of
     * [openSongsMenu]. When an [imageView] is supplied the dialog animates in with a shared
     * image transition; otherwise a plain fade is used.
     *
     * Available actions: Play, Shuffle, Add All to Queue, Share, Edit, Delete.
     *
     * @param item      The [PlaylistWithSongs] whose metadata populates the menu header and
     *                  whose songs power the playback and share actions.
     * @param imageView Optional source [ImageView] for the shared-element transition.
     */
    protected fun openPlaylistMenu(item: PlaylistWithSongs, imageView: ImageView?) {
        val onViewCreated: (DialogPlaylistMenuBinding) -> Unit = { binding ->
            miniPlayerCallbacks?.onHideMiniPlayer()
            binding.title.text = item.playlist.name
            binding.secondaryDetail.text = getString(R.string.x_songs, item.songs.size)
            binding.tertiaryDetail.text = item.playlist.description.orEmpty()
            if (imageView == null) {
                item.songs.firstOrNull()?.let { binding.cover.loadArtCoverWithPayload(it) }
            }
        }

        val onDialogInflated: (DialogPlaylistMenuBinding, () -> Unit, () -> Unit) -> Unit = { binding, dismiss, _ ->
            binding.play.setOnClickListener {
                if (item.songs.isNotEmpty()) setMediaItems(item.songs.toMutableList(), 0)
                dismiss()
            }

            binding.shuffle.setOnClickListener {
                if (item.songs.isNotEmpty()) shuffleMediaItems(item.songs)
                dismiss()
            }

            binding.addAllToQueue.setOnClickListener {
                item.songs.forEach { MediaPlaybackManager.addToQueue(it) }
                dismiss()
            }

            binding.share.setOnClickListener {
                dismiss()
                sharePlaylistSongs(item)
            }

            binding.edit.setOnClickListener {
                dismiss()
                showEditPlaylistDialog(item)
            }

            binding.delete.setOnClickListener {
                dismiss()
                withSureDialog { confirmed ->
                    if (confirmed) deletePlaylist(item)
                }
            }
        }

        val onDismissCallback: () -> Unit = {
            miniPlayerCallbacks?.onShowMiniPlayer()
        }

        if (imageView != null) {
            SimpleSharedImageDialog.Builder(
                    container = requireContainerView(),
                    sourceImageView = imageView,
                    inflateBinding = DialogPlaylistMenuBinding::inflate,
                    targetImageViewProvider = { it.cover })
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        } else {
            SimpleDialog.Builder(
                    container = requireContainerView(),
                    inflateBinding = DialogPlaylistMenuBinding::inflate)
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        }
    }

    /**
     * Shares all songs in [item] as a multi-file send intent.
     * Audio files are already SAF content URIs, so we pass them straight to the
     * chooser with [Intent.FLAG_GRANT_READ_URI_PERMISSION] — no FileProvider needed.
     *
     * @param item The [PlaylistWithSongs] whose songs will be shared.
     */
    private fun sharePlaylistSongs(item: PlaylistWithSongs) {
        val uris = item.songs.map { it.uri.toUri() } as ArrayList<Uri>
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.send)))
    }

    /**
     * Shows the "Edit Playlist" dialog pre-filled with the current [item] name and description.
     * On confirmation the [item.playlist] row is updated in the database via the IO dispatcher.
     *
     * @param item The [PlaylistWithSongs] whose metadata will be edited.
     */
    private fun showEditPlaylistDialog(item: PlaylistWithSongs) {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogEditPlaylistBinding::inflate)
            .onViewCreated { binding ->
                binding.playlistNameInput.setText(item.playlist.name)
                binding.playlistNameInput.requestFocus()
                val description = item.playlist.description
                if (!description.isNullOrEmpty()) {
                    binding.playlistDescriptionInput.setText(description)
                }
            }
            .onDialogInflated { binding, dismiss, _ ->
                binding.cancel.setOnClickListener { dismiss() }

                binding.save.setOnClickListener {
                    val newName = binding.playlistNameInput.text?.toString()?.trim()
                    if (newName.isNullOrEmpty()) return@setOnClickListener
                    val newDescription = binding.playlistDescriptionInput.text
                        ?.toString()?.trim()
                        .takeUnless { it.isNullOrEmpty() }
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val updated = item.playlist.copy(
                                name = newName,
                                description = newDescription,
                                dateModified = System.currentTimeMillis()
                        )
                        AudioDatabase.getInstance(requireContext()).playlistDao().updatePlaylist(updated)
                    }
                    dismiss()
                }
            }
            .setWidthRatio(getDialogWidthRation())
            .onDismiss { /* no-op */ }
            .build()
            .show()
    }

    /**
     * Deletes [item]'s playlist row from the database on the IO dispatcher. All associated
     * cross-ref rows are removed automatically by Room's cascade-delete foreign key.
     *
     * @param item The [PlaylistWithSongs] to delete.
     */
    private fun deletePlaylist(item: PlaylistWithSongs) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            AudioDatabase.getInstance(requireContext()).playlistDao().deletePlaylist(item.playlist)
            Log.d(TAG, "Playlist deleted: ${item.playlist.name}")
        }
    }

    /**
     * Opens the album context menu dialog for the given [album], mirroring the structure of
     * [openPlaylistMenu]. When [imageView] is supplied the dialog animates in with a shared
     * image transition; otherwise a plain fade is used.
     *
     * Songs belonging to this album are fetched lazily from [Album.songPaths] on the IO
     * dispatcher only when a playback or share action is tapped, avoiding the cost of loading
     * full [Audio] objects upfront for every item in the album list.
     *
     * Available actions: Play, Shuffle, Add All to Queue, Go to Artist, Share.
     *
     * @param album     The [Album] whose metadata populates the menu header.
     * @param imageView Optional source [ImageView] for the shared-element transition.
     */
    protected fun openAlbumMenu(album: Album, imageView: ImageView?) {
        val onViewCreated: (DialogAlbumMenuBinding) -> Unit = { binding ->
            miniPlayerCallbacks?.onHideMiniPlayer()
            binding.title.text = album.name
            binding.secondaryDetail.text = album.artist
            binding.tertiaryDetail.text = getString(R.string.x_songs, album.songCount)
            if (imageView == null) {
                binding.cover.loadArtCoverWithPayload(album)
            }
        }

        val onDialogInflated: (DialogAlbumMenuBinding, () -> Unit, () -> Unit) -> Unit = { binding, dismiss, dismissImmediately ->
            binding.play.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(album.songPaths)
                    if (songs.isNotEmpty()) setMediaItems(songs.toMutableList(), 0)
                }
                dismiss()
            }

            binding.shuffle.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(album.songPaths)
                    if (songs.isNotEmpty()) shuffleMediaItems(songs)
                }
                dismiss()
            }

            binding.addAllToQueue.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(album.songPaths)
                    songs.forEach { MediaPlaybackManager.addToQueue(it) }
                }
                dismiss()
            }

            binding.goToArtist.setOnClickListener {
                val artistName = album.artist ?: return@setOnClickListener
                val artist = Artist(
                        id = album.artistId,
                        name = artistName,
                        albumCount = 0,
                        trackCount = album.songCount
                )
                openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                dismissImmediately()
            }

            binding.share.setOnClickListener {
                dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(album.songPaths)
                    if (songs.isNotEmpty()) shareAudioList(songs)
                }
            }
        }

        val onDismissCallback: () -> Unit = {
            miniPlayerCallbacks?.onShowMiniPlayer()
        }

        if (imageView != null) {
            SimpleSharedImageDialog.Builder(
                    container = requireContainerView(),
                    sourceImageView = imageView,
                    inflateBinding = DialogAlbumMenuBinding::inflate,
                    targetImageViewProvider = { it.cover })
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        } else {
            SimpleDialog.Builder(
                    container = requireContainerView(),
                    inflateBinding = DialogAlbumMenuBinding::inflate)
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        }
    }

    /**
     * Opens the artist context menu dialog for the given [artist], mirroring the structure of
     * [openAlbumMenu]. When [imageView] is supplied the dialog animates in with a shared image
     * transition; otherwise a plain fade is used.
     *
     * Available actions: Play, Shuffle, Add All to Queue, Share.
     *
     * @param artist    The [Artist] whose metadata populates the menu header.
     * @param imageView Optional source [ImageView] for the shared-element transition.
     */
    protected fun openArtistMenu(artist: Artist, imageView: ImageView?) {
        val onViewCreated: (DialogArtistMenuBinding) -> Unit = { binding ->
            miniPlayerCallbacks?.onHideMiniPlayer()
            binding.title.text = artist.name
            binding.secondaryDetail.text = getString(R.string.x_albums, artist.albumCount)
            binding.tertiaryDetail.text = getString(R.string.x_songs, artist.trackCount)
            if (imageView == null) {
                binding.cover.loadArtCoverWithPayload(artist)
            }
        }

        val onDialogInflated: (DialogArtistMenuBinding, () -> Unit, () -> Unit) -> Unit = { binding, dismiss, _ ->
            binding.play.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(artist.songPaths)
                    if (songs.isNotEmpty()) setMediaItems(songs.toMutableList(), 0)
                }
                dismiss()
            }

            binding.shuffle.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(artist.songPaths)
                    if (songs.isNotEmpty()) shuffleMediaItems(songs)
                }
                dismiss()
            }

            binding.addAllToQueue.setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(artist.songPaths)
                    songs.forEach { MediaPlaybackManager.addToQueue(it) }
                }
                dismiss()
            }

            binding.share.setOnClickListener {
                dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    val songs = fetchAudiosByPaths(artist.songPaths)
                    if (songs.isNotEmpty()) shareAudioList(songs)
                }
            }
        }

        val onDismissCallback: () -> Unit = {
            miniPlayerCallbacks?.onShowMiniPlayer()
        }

        if (imageView != null) {
            SimpleSharedImageDialog.Builder(
                    container = requireContainerView(),
                    sourceImageView = imageView,
                    inflateBinding = DialogArtistMenuBinding::inflate,
                    targetImageViewProvider = { it.cover })
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        } else {
            SimpleDialog.Builder(
                    container = requireContainerView(),
                    inflateBinding = DialogArtistMenuBinding::inflate)
                .onViewCreated(onViewCreated)
                .onDialogInflated(onDialogInflated)
                .onDismiss(onDismissCallback)
                .setWidthRatio(getDialogWidthRation())
                .build()
                .show()
        }
    }

    /**
     * Fetches full [Audio] objects for the given list of file [paths] from the local database.
     * Runs on the IO dispatcher and silently skips any path whose record is not found.
     *
     * @param paths The ordered list of file paths to resolve.
     * @return Resolved [Audio] objects in the same order as [paths], with missing entries omitted.
     */
    private suspend fun fetchAudiosByPaths(paths: List<String>): List<Audio> {
        return withContext(Dispatchers.IO) {
            val dao = AudioDatabase.getInstance(requireContext()).audioDao()
                ?: return@withContext emptyList<Audio>()
            paths.mapNotNull { path -> dao.getAudioByPath(path) }
        }
    }

    /**
     * Shares all [audios] as a multi-file send intent.
     * Audio files are already SAF content URIs, so we pass them straight to the
     * chooser with [Intent.FLAG_GRANT_READ_URI_PERMISSION] — no FileProvider needed.
     *
     * @param audios The list of [Audio] tracks to share.
     */
    private fun shareAudioList(audios: List<Audio>) {
        val uris = audios.map { it.uri.toUri() } as ArrayList<Uri>
        if (uris.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.send)))
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

        const val SEEK_PER_PULSE_MS = 2500L
        private const val SEEK_INTERVAL_MS = 500L
    }
}