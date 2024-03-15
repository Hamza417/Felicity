package app.simple.felicity.extensions.viewmodels

import android.app.Application
import app.simple.felicity.loaders.MediaLoader.loadAlbums
import app.simple.felicity.loaders.MediaLoader.loadAudios
import app.simple.felicity.models.Album
import app.simple.felicity.models.Audio

open class DatabaseLoaderViewModel(application: Application) : WrappedViewModel(application) {

    protected fun getAudios(): ArrayList<Audio> {
        return applicationContext().loadAudios()
    }

    protected fun getAlbums(): ArrayList<Album> {
        return applicationContext().loadAlbums()
    }
}
