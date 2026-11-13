package app.simple.felicity.repository.models

import com.google.gson.annotations.SerializedName

data class LrcLibResponse(
        @SerializedName("id") val id: Int,
        @SerializedName("name") val name: String?,
        @SerializedName("trackName") val trackName: String,
        @SerializedName("artistName") val artistName: String,
        @SerializedName("albumName") val albumName: String?,
        @SerializedName("duration") val duration: Double,
        @SerializedName("instrumental") val isInstrumental: Boolean,
        @SerializedName("hasWordSync") val hasWordSync: Boolean,
        @SerializedName("plainLyrics") val plainLyrics: String?,
        @SerializedName("syncedLyrics") val syncedLyrics: String?, // This is the .lrc content
        @SerializedName("lyricsfile") val lyricsfile: String?
)