package app.simple.felicity.extensions.fragments

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.databinding.DialogSongMenuBinding
import app.simple.felicity.decorations.popups.SimpleSharedImageDialog
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.interfaces.MiniPlayerPolicy
import app.simple.felicity.preferences.PlayerPreferences
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.database.instances.SongStatDatabase
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.managers.PlaybackStateManager
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.Song
import app.simple.felicity.repository.utils.SongUtils
import app.simple.felicity.repository.utils.SongUtils.createSongStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class MediaFragment : ScopedFragment(), MiniPlayerPolicy {

    private var shouldShowMiniPlayer = true

    private val miniPlayerCallbacks: MiniPlayerCallbacks?
        get() = requireActivity() as? MiniPlayerCallbacks

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songSeekPositionFlow.collect { position ->
                PlayerPreferences.setLastSongSeek(position)
                onSeekChanged(position)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            MediaManager.songPositionFlow.collect { position ->
                Log.d(TAG, "Song position: $position")
                PlayerPreferences.setLastSongPosition(position)
                MediaManager.getCurrentSong()?.let { song ->
                    onAudio(song)
                }
                onPositionChanged(position)
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
        PlayerPreferences.setLastSongPosition(position)
        PlayerPreferences.setLastSongId(songs.getOrNull(position)?.id ?: -1L)
        MediaManager.setSongs(songs, position)
        MediaManager.play()
        createSongHistoryDatabase(songs)
        // createStatForSong(songs[position])
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

    private fun createStatForSong(song: Song) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val songStatDatabase = SongStatDatabase.getInstance(requireContext())
            val songStatDao = songStatDatabase.songStatDao()
            val existingStat = songStatDao.getSongStatByStableId(SongUtils.generateStableId(song))

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
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                shouldShowMiniPlayer = false
                hideMiniPlayer()
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                // Don't force-show during configuration changes; preserve current state
                if (requireActivity().isChangingConfigurations.not()) {
                    shouldShowMiniPlayer = true
                    showMiniPlayer()
                }
            }
        })
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
        Log.d(TAG, "Seek changed: $seek")
    }

    protected fun openSongsMenu(audios: List<Audio>, position: Int, imageView: ImageView) {
        SimpleSharedImageDialog.Builder(
                container = requireContainerView(),
                sourceImageView = imageView,
                inflateBinding = DialogSongMenuBinding::inflate,
                targetImageViewProvider = { it.cover })
            .onViewCreated { binding ->
                binding.cover.loadArtCoverWithPayload(audios[position])
                binding.title.text = audios[position].title
                binding.secondaryDetail.text = audios[position].artist
                binding.tertiaryDetail.text = audios[position].album
            }.onDialogInflated { binding, dismiss ->
                binding.play.setOnClickListener {
                    val pos = audios.indexOfFirst { it.id == audios[position].id }.coerceAtLeast(0)
                    setMediaItems(audios, pos)
                    postDelayed {
                        dismiss()
                    }
                }

                binding.addToQueue.setOnClickListener {
                    dismiss()
                }

                binding.addToPlaylist.setOnClickListener {

                }
            }
            .build()
            .show()
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = shouldShowMiniPlayer

    companion object {
        private const val TAG = "MediaFragment"
    }
}