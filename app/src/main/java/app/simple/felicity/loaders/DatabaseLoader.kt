package app.simple.felicity.loaders

import android.content.Context
import app.simple.felicity.repository.database.instances.AudioDatabase
import app.simple.felicity.repository.models.normal.Audio
import app.simple.felicity.utils.ArrayUtils.toArrayList

object DatabaseLoader {

    suspend fun Context.loadAudio(): ArrayList<Audio> {
        return AudioDatabase.getInstance(this)?.audioDao()?.getAllAudio()!!.toArrayList()
    }
}
