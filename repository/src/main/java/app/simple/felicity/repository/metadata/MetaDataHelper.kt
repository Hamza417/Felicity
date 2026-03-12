package app.simple.felicity.repository.metadata

import android.util.Log
import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.repository.models.Audio
import java.io.File

object MetaDataHelper {

    private const val TAG = "MetaDataHelper"

    fun File.extractMetadata(): Audio? {
        return runCatching {
            JAudioMetadataLoader.loadFromFile(this)
        }.getOrElse { it ->
            Log.e(TAG, "Failed to load metadata using JAudioMetadataLoader for file: ${this.absolutePath}")
            it.printStackTrace()
            runCatching {
                Log.d(TAG, "Attempting to load metadata using MediaMetadataLoader for file: ${this.absolutePath}")
                MediaMetadataLoader.loadFromFile(this)
            }.getOrElse {
                Log.e(TAG, "Failed to load metadata using MediaMetadataLoader for file: ${this.absolutePath}")
                it.printStackTrace()
                null
            }
        }
    }

    fun String.extractMetadata(): Audio? {
        return this.toFile().extractMetadata()
    }
}
