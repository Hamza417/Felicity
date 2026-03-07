package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.databinding.DialogSureBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.decorations.popups.SimpleSharedImageDialog
import app.simple.felicity.dialogs.app.AudioInformation.Companion.showAudioInfo
import app.simple.felicity.dialogs.lyrics.Lyrics.Companion.showLyrics
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.database.instances.SongStatDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.models.Album
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils
import app.simple.felicity.repository.utils.AudioUtils.createSongStat
import app.simple.felicity.shared.utils.BitmapUtils.toBitmap
import app.simple.felicity.shared.utils.ViewUtils.gone
import app.simple.felicity.ui.pages.AlbumPage
import app.simple.felicity.ui.pages.ArtistPage
import app.simple.felicity.ui.panels.PlayingQueue
import app.simple.felicity.ui.player.DefaultPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

open class MediaFragment : ScopedFragment(), MiniPlayerPolicy {

    private var shouldShowMiniPlayer = true
    private var lastSavedSeekPosition = 0L

    private val miniPlayerCallbacks: MiniPlayerCallbacks?
        get() = requireActivity() as? MiniPlayerCallbacks

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                onSeekChanged(position)

                // Save to database every 5 seconds or 5% of duration, whichever is larger
                val song = MediaManager.getCurrentSong()
                if (song != null) {
                    val threshold = maxOf(5000L, song.duration / 20) // 5 seconds or 5% of duration
                    if (kotlin.math.abs(position - lastSavedSeekPosition) > threshold) {
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
                MediaManager.songPositionFlow.collect { position ->
                    Log.d(TAG, "Song position: $position")
                    MediaManager.getCurrentSong()?.let { song ->
                        onAudio(song)
                    }
                    onPositionChanged(position)
                    // Save state when song position changes
                    saveCurrentPlaybackState()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songListFlow.collect { songs ->
                Log.d(TAG, "Song list updated: ${songs.size} songs")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.playbackStateFlow.collect { state ->
                onPlaybackStateChanged(state)
            }
        }
    }

    protected fun setMediaItems(songs: List<Audio>, position: Int = 0) {
        val currentSong = MediaManager.getCurrentSong()
        val requestedSong = songs.getOrNull(position)
        val isSameQueue = MediaManager.isSameQueue(songs)
        val isSameSong = currentSong != null && requestedSong != null && currentSong.id == requestedSong.id

        when {
            isSameQueue && isSameSong -> {
                // Case 1: Same queue, same song — just open the player
                openDefaultPlayer()
            }
            isSameSong -> {
                // Case 2: Same song playing but queue is different — update queue silently and open player
                updateQueueSilently(songs, position)
            }
            isSameQueue && !isSameSong -> {
                // Case 4: Same queue but different song — seek to that position and open player
                MediaManager.updatePosition(position)
            }
            else -> {
                // Case 3: Different queue and different song — default behavior
                MediaManager.setSongs(songs, position)
                MediaManager.play()
                createSongHistoryDatabase(songs)
            }
        }
    }

    private fun openDefaultPlayer() {
        openFragment(DefaultPlayer.newInstance(), DefaultPlayer.TAG)
    }

    private fun updateQueueSilently(songs: List<Audio>, position: Int) {
        MediaManager.updateQueueSilently(songs, position)
        createSongHistoryDatabase(songs)
    }

    private fun createSongHistoryDatabase(songs: List<Audio>) {
        val seek = MediaManager.getSeekPosition()
        val idx = MediaManager.getCurrentPosition()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val audioDatabase = AudioDatabase.getInstance(requireContext())
            PlaybackStateManager.savePlaybackState(
                    db = audioDatabase,
                    queueIds = songs.map { it.id },
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

    private fun createStatForSong(song: Audio) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val songStatDatabase = SongStatDatabase.getInstance(requireContext())
            val songStatDao = songStatDatabase.songStatDao()
            val existingStat = songStatDao.getSongStatByStableId(AudioUtils.generateStableId(song))

            if (existingStat == null) {
                songStatDao.insertSongStat(song.createSongStat(existingStat))
                Log.d(TAG, "Created new song stat for: ${song.title}")
            } else {
                songStatDao.updateSongStat(existingStat.copy(playCount = existingStat.playCount + 1))
            }
        }
    }

    protected fun requireHiddenMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                super.onCreate(owner)
                shouldShowMiniPlayer = false
                hideMiniPlayer()
            }

            // TODO fix delay in showing up?
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

    protected fun openSongsMenu(audios: List<Audio>, position: Int, imageView: ImageView) {
        SimpleSharedImageDialog.Builder(
                container = requireContainerView(),
                sourceImageView = imageView,
                inflateBinding = DialogSongMenuBinding::inflate,
                targetImageViewProvider = { it.cover })
            .onViewCreated { binding ->
                binding.cover.setImageBitmap(imageView.drawable.toBitmap())
                binding.title.text = audios[position].title
                binding.secondaryDetail.text = audios[position].artist
                binding.tertiaryDetail.text = audios[position].album

                // Hide "Add to Queue" and "Play Next" when the song is already playing
                val isCurrentlyPlaying = MediaManager.getCurrentSong()?.id == audios[position].id
                if (isCurrentlyPlaying) {
                    binding.addToQueue.gone(animate = false)
                    binding.playNext.gone(animate = false)
                }

                // Hide "Go to Artist" if artist is unknown/empty
                if (audios[position].artist.isNullOrBlank()) {
                    binding.goToArtist.gone(animate = false)
                }

                // Hide "Go to Album" if album is unknown/empty
                if (audios[position].album.isNullOrBlank()) {
                    binding.goToAlbum.gone(animate = false)
                }
            }.onDialogInflated { binding, dismiss ->
                binding.play.setOnClickListener {
                    val pos = audios.indexOfFirst { it.id == audios[position].id }.coerceAtLeast(0)
                    setMediaItems(audios, pos)
                    dismiss()
                }

                binding.addToQueue.setOnClickListener {
                    MediaManager.addToQueue(audios[position])
                    openFragment(PlayingQueue.newInstance(), PlayingQueue.TAG)
                    dismiss()
                }

                binding.playNext.setOnClickListener {
                    MediaManager.playNext(audios[position])
                    dismiss()
                }

                binding.addToPlaylist.setOnClickListener {

                }

                binding.goToArtist.setOnClickListener {
                    val audio = audios[position]
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
                    val audio = audios[position]
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

                binding.share.setOnClickListener {
                    val file = audios[position].file
                    val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.provider",
                            file
                    )

                    ShareCompat.IntentBuilder(requireContext())
                        .setType("audio/*")
                        .setText(audios[position].title)
                        .setStream(uri)
                        .startChooser()
                }

                binding.delete.setOnClickListener {
                    dismiss()
                    showDeleteConfirmation() { confirmed ->
                        if (confirmed) {
                            deleteSong(audios[position])
                        } else {
                            // Reopen the menu if user cancels
                            openSongsMenu(audios, position, imageView)
                        }
                    }
                }

                binding.lyrics.setOnClickListener {
                    childFragmentManager.showLyrics(audios[position]).also {
                        dismiss()
                    }
                }

                binding.info.setOnClickListener {
                    childFragmentManager.showAudioInfo(audios[position]).also {
                        dismiss()
                    }
                }
            }
            .build()
            .show()
    }

    protected fun showDeleteConfirmation(onResult: (Boolean) -> Unit) {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogSureBinding::inflate)
            .onViewCreated { binding ->

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
            .build()
            .show()
    }

    private fun deleteSong(audio: Audio) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Delete the physical file
                val file = File(audio.path)
                val deleted = if (file.exists()) {
                    file.delete()
                } else {
                    Log.w(TAG, "File does not exist: ${audio.path}")
                    true // Consider it deleted if it doesn't exist
                }

                if (deleted) {
                    // Remove from database
                    val audioDatabase = AudioDatabase.getInstance(requireContext())
                    audioDatabase.audioDao()?.delete(audio)

                    // Remove the song from the queue (this handles skip-to-next if it's playing)
                    val queueIndex = MediaManager.getSongs().indexOfFirst { it.id == audio.id }
                    if (queueIndex != -1) {
                        // removeQueueItemSilently handles skip/playback continuation
                        MediaManager.removeQueueItemSilently(queueIndex)
                    } else if (MediaManager.getCurrentSong()?.id == audio.id) {
                        // Song was playing but not in queue list — just skip
                        MediaManager.next()
                    }

                    Log.d(TAG, "Song deleted successfully: ${audio.title}")
                } else {
                    Log.e(TAG, "Failed to delete file: ${audio.path}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting song: ${e.message}", e)
            }
        }
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = shouldShowMiniPlayer

    companion object {
        private const val TAG = "MediaFragment"
    }
}