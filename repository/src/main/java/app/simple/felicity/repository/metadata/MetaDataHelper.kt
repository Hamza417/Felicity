package app.simple.felicity.repository.metadata

import app.simple.felicity.core.utils.FileUtils.toFile
import app.simple.felicity.repository.models.Audio
import java.io.File

object MetaDataHelper {
    fun File.extractMetadata(): Audio? {
        return runCatching {
            JAudioMetadataLoader.loadFromFile(this)
        }.getOrElse {
            it.printStackTrace()
            runCatching {
                MediaMetadataLoader.loadFromFile(this)
            }.getOrNull()
        }
    }

    fun String.extractMetadata(): Audio? {
        return this.toFile().extractMetadata()
    }
}