package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.repository.constants.FileConstants.getAudioFormat
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.repositories.LrcRepository
import app.simple.felicity.shared.utils.TimeUtils.toDynamicTimeString
import app.simple.felicity.utils.DateUtils.toDate
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads and prepares all the metadata for a single audio file so the dialog
 * can display it without doing any heavy work on the UI thread.
 *
 * @author Hamza417
 */
@HiltViewModel(assistedFactory = AudioInformationViewModel.Factory::class)
class AudioInformationViewModel @AssistedInject constructor(
        application: Application,
        @Assisted private val audio: Audio,
        private val lrcRepository: LrcRepository
) : AndroidViewModel(application) {

    private val _info = MutableStateFlow<AudioInfo?>(null)

    val info: StateFlow<AudioInfo?> = _info.asStateFlow()

    init {
        loadInfo()
    }

    private fun loadInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasEmbeddedArt = checkEmbeddedArt(audio.uri)
            val hasLrc = lrcRepository.lrcFileExists(audio.uri)
            val app = getApplication<Application>()

            _info.emit(
                    AudioInfo(
                            title = audio.title ?: audio.name ?: "–",
                            path = audio.path ?: "–",
                            album = audio.album ?: "–",
                            artist = audio.artist ?: "–",
                            albumArtist = audio.albumArtist ?: "–",
                            duration = audio.duration.toDynamicTimeString(),
                            size = Formatter.formatShortFileSize(app, audio.size),
                            bitrate = "${audio.bitrate} kbps",
                            sampleRate = "${audio.samplingRate} Hz",
                            bitDepth = if (audio.bitPerSample > 0) "${audio.bitPerSample}-bit" else "–",
                            mimeType = audio.mimeType ?: "–",
                            format = audio.uri.getAudioFormat() ?: "–",
                            genre = audio.genre ?: "–",
                            year = audio.year ?: "–",
                            track = if (audio.track > 0) audio.track.toString() else "–",
                            trackNumber = audio.trackNumber ?: "–",
                            numTracks = audio.numTracks ?: "–",
                            disc = audio.discNumber ?: "–",
                            composer = audio.composer ?: "–",
                            author = audio.author ?: "–",
                            writer = audio.writer ?: "–",
                            compilation = audio.compilation ?: "–",
                            date = audio.date ?: "–",
                            dateAdded = if (audio.dateAdded > 0) audio.dateAdded.toDate() else "–",
                            dateModified = if (audio.dateModified > 0) audio.dateModified.toDate() else "–",
                            dateTaken = if (audio.dateTaken > 0) audio.dateTaken.toDate() else "–",
                            hasEmbeddedArt = app.getString(if (hasEmbeddedArt) R.string.yes else R.string.no),
                            hasLrc = app.getString(if (hasLrc) R.string.yes else R.string.no),
                            audioId = audio.id.toString(),
                    )
            )
        }
    }

    private fun checkEmbeddedArt(path: String?): Boolean {
        if (path == null) return false
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val art = retriever.embeddedPicture
            retriever.release()
            art != null
        } catch (_: Exception) {
            false
        }
    }

    data class AudioInfo(
            val title: String,
            val path: String,
            val album: String,
            val artist: String,
            val albumArtist: String,
            val duration: String,
            val size: String,
            val bitrate: String,
            val sampleRate: String,
            val bitDepth: String,
            val mimeType: String,
            val format: String,
            val genre: String,
            val year: String,
            val track: String,
            val trackNumber: String,
            val numTracks: String,
            val disc: String,
            val composer: String,
            val author: String,
            val writer: String,
            val compilation: String,
            val date: String,
            val dateAdded: String,
            val dateModified: String,
            val dateTaken: String,
            val hasEmbeddedArt: String,
            val hasLrc: String,
            val audioId: String,
    )

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio): AudioInformationViewModel
    }
}
