package app.simple.felicity.extensions.viewmodels

import android.app.Application
import app.simple.felicity.loaders.MediaStoreLoader.loadAlbums
import app.simple.felicity.loaders.MediaStoreLoader.loadAudios
import app.simple.felicity.repository.models.normal.Album
import app.simple.felicity.repository.models.normal.Audio

open class DatabaseLoaderViewModel(application: Application) : WrappedViewModel(application) {

    protected fun getAudios(): ArrayList<Audio> {
        return applicationContext().loadAudios()
    }

    protected fun getAlbums(): ArrayList<Album> {
        return applicationContext().loadAlbums()
    }
}
