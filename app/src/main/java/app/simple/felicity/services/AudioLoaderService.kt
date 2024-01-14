package app.simple.felicity.services

import android.app.Service
import android.content.Intent
import android.content.UriPermission
import android.os.IBinder
import androidx.documentfile.provider.DocumentFile
import app.simple.felicity.utils.ArrayUtils.toArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AudioLoaderService : Service() {

    private var directories: ArrayList<UriPermission> = ArrayList()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        directories = applicationContext.contentResolver.persistedUriPermissions.toArrayList()
        loadAudio(directories)
    }

    private fun loadAudio(directories: ArrayList<UriPermission>) {
        CoroutineScope(Dispatchers.IO).launch {
            for (directory in directories) {
                DocumentFile.fromTreeUri(applicationContext, directory.uri)?.listFiles()?.forEach {
                    val mediaMetadataRetriever = android.media.MediaMetadataRetriever()
                }
            }
        }
    }
}