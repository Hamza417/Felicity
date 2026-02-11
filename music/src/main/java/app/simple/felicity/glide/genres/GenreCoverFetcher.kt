package app.simple.felicity.glide.genres

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Size
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import app.simple.felicity.R
import app.simple.felicity.preferences.GenresPreferences
import app.simple.felicity.repository.covers.GenreCover
import app.simple.felicity.repository.maps.GenreMap
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.shared.helpers.ImageHelper.toBitmap
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.io.FileNotFoundException
import java.util.Locale

class GenreCoverFetcher internal constructor(private val context: Context, private val genre: Genre) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        when {
            GenresPreferences.isGenreCoversEnabled() -> {
                // Try to load from actual audio files first
                val coverFromFiles = GenreCover.load(genre)
                if (coverFromFiles != null) {
                    callback.onDataReady(coverFromFiles)
                } else {
                    // Fallback to genre-mapped cover images
                    callback.onDataReady(BitmapFactory.decodeResource(
                            context.resources,
                            GenreMap.getGenreImage(genre = (genre.name ?: "").lowercase(Locale.getDefault()))))
                }
            }
            else -> {
                // TODO
                val albumArts = emptyList<String>().toMutableList()

                val count = when {
                    albumArts.size >= 9 -> 9
                    albumArts.size >= 4 -> 4
                    else -> 1
                }

                val (gridCols, gridRows) = when (count) {
                    1 -> 1 to 1
                    4 -> 2 to 2
                    else -> 3 to 3
                }

                val cellSize = 512
                val canvasWidth = gridCols * cellSize
                val canvasHeight = gridRows * cellSize

                val bitmaps = mutableListOf<Bitmap>()
                for (str in albumArts.take(count)) {
                    val uri = str.toUri()
                    val bmp = try {
                        context.contentResolver.loadThumbnail(uri, Size(cellSize, cellSize), null)
                    } catch (e: FileNotFoundException) {
                        R.drawable.ic_felicity_full_art.toBitmap(context)
                    }
                    bmp.let { bitmaps.add(it) }
                }

                if (bitmaps.isEmpty()) {
                    callback.onLoadFailed(Exception("No album art found"))
                    return
                }

                val result = createBitmap(canvasWidth, canvasHeight)
                val canvas = Canvas(result)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                if (count == 1) {
                    val scaled = bitmaps[0].scale(canvasWidth, canvasHeight)
                    canvas.drawBitmap(scaled, 0f, 0f, paint)
                    if (scaled != bitmaps[0]) scaled.recycle()
                } else {
                    for (i in 0 until bitmaps.size) {
                        val row = i / gridCols
                        val col = i % gridCols
                        val left = col * cellSize
                        val top = row * cellSize
                        val bmp = bitmaps[i]
                        val scaled = bmp.scale(cellSize, cellSize)
                        canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), paint)
                        if (scaled != bmp) scaled.recycle()
                    }
                }

                callback.onDataReady(result)
            }
        }
    }

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass() = Bitmap::class.java
    override fun getDataSource() = DataSource.LOCAL
}