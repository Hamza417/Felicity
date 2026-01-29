package app.simple.felicity.repository.models

import android.net.Uri

data class LrcFile(
        val name: String,
        val uri: Uri,
        val parentId: String
)