package app.simple.felicity.extensions.fragments

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.simple.felicity.engine.services.ExoPlayerService
import app.simple.felicity.repository.models.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

open class MediaFragment : ScopedFragment() {

    protected var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionToken =
            SessionToken(requireActivity(),
                         ComponentName(requireActivity(), ExoPlayerService::class.java))

        Log.d(TAG, "onViewCreated: SessionToken: $sessionToken")
        controllerFuture =
            MediaController.Builder(requireActivity(), sessionToken).buildAsync()

        val listener = Runnable {
            Log.d(TAG, "onViewCreated: MediaController created successfully")
            mediaController = controllerFuture?.get()
        }

        controllerFuture?.addListener(listener, MoreExecutors.directExecutor())
    }

    protected fun setMediaItems(songs: List<Song>, position: Int = 0) {
        mediaController?.let { controller ->
            val mediaItems = songs.map { song ->
                MediaItem.Builder()
                    .setMediaId(song.id.toString())
                    .setUri(song.uri)
                    .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setArtist(song.artist)
                                .setTitle(song.title)
                                .build()
                    )
                    .build()
            }
            controller.setMediaItems(mediaItems, position, 0L)
            controller.prepare()
            controller.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            MediaController.releaseFuture(controllerFuture!!)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "MediaFragment"
    }
}