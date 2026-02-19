package app.simple.felicity.viewmodels.dialogs

import android.app.Application
import android.media.MediaMetadataRetriever
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.simple.felicity.R
import app.simple.felicity.adapters.dialogs.AdapterAudioInformation.Data
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

@HiltViewModel(assistedFactory = AudioInformationViewModel.Factory::class)
class AudioInformationViewModel @AssistedInject constructor(
        application: Application,
        @Assisted private val audio: Audio,
        private val lrcRepository: LrcRepository
) : AndroidViewModel(application) {

    private val _info = MutableStateFlow<List<Data>>(emptyList())
    val info: StateFlow<List<Data>> = _info.asStateFlow()

    init {
        loadInfo()
    }

    private fun loadInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasEmbeddedArt = checkEmbeddedArt(audio.path)
            val hasLrc = lrcRepository.lrcFileExists(audio.path)

            val list = buildList {
                // ── Full-span rows (wide data) ──────────────────────────────
                add(data(R.string.title, audio.title ?: audio.name ?: "–", fullSpan = true))
                add(data(R.string.path, audio.path ?: "–", fullSpan = true))
                add(data(R.string.album, audio.album ?: "–", fullSpan = true))

                // ── Half-span rows (2-column grid) ──────────────────────────
                add(data(R.string.artist, audio.artist ?: "–"))
                add(data(R.string.album_artist, audio.albumArtist ?: "–"))
                add(data(R.string.duration, audio.duration.toDynamicTimeString()))
                add(data(R.string.size, Formatter.formatShortFileSize(getApplication(), audio.size)))
                add(data(R.string.bitrate, "${audio.bitrate / 1000} kbps"))
                add(data(R.string.sample_rate, "${audio.samplingRate} Hz"))
                add(data(R.string.bit_depth,
                         if (audio.bitPerSample > 0) "${audio.bitPerSample}-bit" else "–"))
                add(data(R.string.mime_type, audio.mimeType ?: "–"))
                add(data(R.string.format, audio.path.getAudioFormat() ?: "–"))
                add(data(R.string.genre, audio.genre ?: "–"))
                add(data(R.string.year, audio.year ?: "–"))
                add(data(R.string.track, if (audio.track > 0) audio.track.toString() else "–"))
                add(data(R.string.track_number, audio.trackNumber ?: "–"))
                add(data(R.string.num_tracks, audio.numTracks ?: "–"))
                add(data(R.string.disc, audio.discNumber ?: "–"))
                add(data(R.string.composer, audio.composer ?: "–"))
                add(data(R.string.author, audio.author ?: "–"))
                add(data(R.string.writer, audio.writer ?: "–"))
                add(data(R.string.compilation, audio.compilation ?: "–"))
                add(data(R.string.date, audio.date ?: "–"))

                // Dates
                add(data(R.string.date_added,
                         if (audio.dateAdded > 0) audio.dateAdded.toDate() else "–"))
                add(data(R.string.date_modified,
                         if (audio.dateModified > 0) audio.dateModified.toDate() else "–"))
                add(data(R.string.date_taken,
                         if (audio.dateTaken > 0) audio.dateTaken.toDate() else "–"))

                // Metadata presence flags
                add(data(R.string.has_embedded_art,
                         getApplication<Application>().getString(
                                 if (hasEmbeddedArt) R.string.yes else R.string.no)))
                add(data(R.string.has_lrc_sidecar,
                         getApplication<Application>().getString(
                                 if (hasLrc) R.string.yes else R.string.no)))

                // Audio ID
                add(data(R.string.audio_id, audio.id.toString()))
            }

            _info.emit(list)
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
        } catch (e: Exception) {
            false
        }
    }

    private fun data(type: Int, value: String, fullSpan: Boolean = false) =
        Data(type = type, value = value, isFullSpan = fullSpan)

    @AssistedFactory
    interface Factory {
        fun create(audio: Audio): AudioInformationViewModel
    }

    companion object {
        private const val TAG = "AudioInformationViewModel"
    }
}
