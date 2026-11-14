package app.simple.felicity.repository.models

import com.google.gson.annotations.SerializedName
import java.util.regex.Pattern

data class LrcLibResponse(
        @SerializedName("id") val id: Int,
        @SerializedName("name") val name: String?,
        @SerializedName("trackName") val trackName: String,
        @SerializedName("artistName") val artistName: String,
        @SerializedName("albumName") val albumName: String?,
        @SerializedName("duration") val duration: Double,
        @SerializedName("instrumental") val isInstrumental: Boolean,
        @SerializedName("hasWordSync") var hasWordSync: Boolean,
        @SerializedName("plainLyrics") val plainLyrics: String?,
        @SerializedName("syncedLyrics") val syncedLyrics: String?, // This is the .lrc content
        @SerializedName("lyricsfile") val lyricsfile: String?
) {
    @Transient
    var hasLineSync: Boolean = false

    fun processSyncState() {
        val syncedStr = syncedLyrics ?: ""
        hasWordSync = WORD_SYNC_PATTERN.matcher(syncedStr).find()
        hasLineSync = LINE_SYNC_PATTERN.matcher(syncedStr).find()
    }

    companion object {
        private val WORD_SYNC_PATTERN = Pattern.compile("<\\d{1,2}:\\d{2}\\.\\d{2,3}>")
        private val LINE_SYNC_PATTERN = Pattern.compile("\\[\\d{1,2}:\\d{2}\\.\\d{2,3}]")
    }
}