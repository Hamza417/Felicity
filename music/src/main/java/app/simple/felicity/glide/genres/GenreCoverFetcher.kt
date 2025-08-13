package app.simple.felicity.glide.genres

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.simple.felicity.repository.maps.GenreMap
import app.simple.felicity.repository.models.Genre
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import java.util.Locale

class GenreCoverFetcher internal constructor(private val context: Context, private val genre: Genre) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        //        val albumArts = GenreRepository(context).fetchAlbumArtUrisForGenre(model.genreId, count = 12)
        //        val bitmaps = mutableListOf<Bitmap>()
        //
        //        // Set grid to 3 columns and 4 rows
        //        val gridCols = 3
        //        val gridRows = 4
        //        val cellSize = 512 // or any desired cell size
        //        val canvasWidth = gridCols * cellSize
        //        val canvasHeight = gridRows * cellSize
        //
        //        for (str in albumArts) {
        //            val uri = str.toUri()
        //            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        //                context.contentResolver.loadThumbnail(uri, Size(cellSize, cellSize), null).let {
        //                    bitmaps.add(it)
        //                }
        //            } else {
        //                context.contentResolver.openInputStream(uri)?.use { input ->
        //                    BitmapFactory.decodeStream(input)?.let {
        //                        bitmaps.add(it)
        //                    }
        //                }
        //            }
        //            if (bitmaps.size == gridCols * gridRows) break
        //        }
        //
        //        if (bitmaps.isEmpty()) {
        //            callback.onLoadFailed(Exception("No album art found"))
        //            return
        //        }
        //
        //        val result = createBitmap(canvasWidth, canvasHeight)
        //        val canvas = Canvas(result)
        //        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        //
        //        // Draw only available images, leave remaining cells blank
        //        for (i in 0 until bitmaps.size) {
        //            val row = i / gridCols
        //            val col = i % gridCols
        //            val left = col * cellSize
        //            val top = row * cellSize
        //            val bmp = bitmaps[i]
        //            val scaled = bmp.scale(cellSize, cellSize)
        //            canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), paint)
        //            if (scaled != bmp) scaled.recycle()
        //        }
        //
        //        // Add black tint
        //        val tintPaint = Paint()
        //        tintPaint.color = android.graphics.Color.BLACK
        //        tintPaint.alpha = 255.times(0.5f).toInt()
        //        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), tintPaint)

        callback.onDataReady(BitmapFactory.decodeResource(
                context.resources,
                GenreMap.getGenreImage(genre = (genre.name ?: "").lowercase(Locale.getDefault()))))
    }

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass() = Bitmap::class.java
    override fun getDataSource() = DataSource.LOCAL
}