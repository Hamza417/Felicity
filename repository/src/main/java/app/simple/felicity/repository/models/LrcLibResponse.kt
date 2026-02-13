package app.simple.felicity.repository.models

import com.google.gson.annotations.SerializedName

data class LrcLibResponse(
        @SerializedName("id") val id: Int,
        @SerializedName("trackName") val trackName: String,
        @SerializedName("artistName") val artistName: String,
        @SerializedName("albumName") val albumName: String?,
        @SerializedName("duration") val duration: Double,
        @SerializedName("plainLyrics") val plainLyrics: String?,
        @SerializedName("syncedLyrics") val syncedLyrics: String?, // This is the .lrc content
        @SerializedName("instrumental") val isInstrumental: Boolean
)