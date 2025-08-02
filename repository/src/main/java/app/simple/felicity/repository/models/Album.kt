package app.simple.felicity.repository.models

import android.net.Uri

data class Album(
        val id: Long,
        val name: String,
        val artist: String,
        val artistId: Long,
        val artworkUri: Uri? = null,
        val songCount: Int = 0
)