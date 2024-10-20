package app.simple.felicity.glide.artistcover

import android.graphics.Bitmap
import android.net.Uri
import app.simple.felicity.R
import app.simple.felicity.core.utils.BitmapUtils.toBitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ArtistCoverFetcher internal constructor(private val artistCoverModel: ArtistCoverModel) : DataFetcher<Bitmap> {

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        try {

        } catch (_: IllegalArgumentException) {
        } catch (e: FileNotFoundException) {
            callback.onDataReady(R.drawable.ic_felicity.toBitmap(artistCoverModel.context, app.simple.felicity.preferences.AppearancePreferences.getIconSize()))
        }
    }

    override fun cleanup() {
        // Cleared
    }

    override fun cancel() {
        // Probably already cleared
    }

    override fun getDataClass(): Class<Bitmap> {
        return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }

    fun fetchArtistImageFromMusicBrainz(artistId: String): String {
        val url = URL("http://musicbrainz.org/ws/2/artist/$artistId?inc=url-rels&fmt=json")

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"  // optional default is GET

            println("\nSent 'GET' request to URL : $url; Response Code : $responseCode")

            BufferedReader(InputStreamReader(inputStream)).use {
                val response = it.readText()
                it.close()
                return response
            }
        }
    }

    companion object {
        private const val TAG = "AlbumCoverFetcher"
        private val ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")
    }
}
