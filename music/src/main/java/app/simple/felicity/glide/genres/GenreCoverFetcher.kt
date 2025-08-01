package app.simple.felicity.glide.genres

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.Size
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import app.simple.felicity.repository.repositories.GenreRepository
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

class GenreCoverFetcher internal constructor(private val model: GenreCoverModel) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val context = model.context
        val albumArts = GenreRepository(context).fetchAlbumArtUrisForGenre(model.genreId, count = 9)
        val bitmaps = mutableListOf<Bitmap>()

        // Load all available bitmaps (up to 9)

        // Decide grid size based on number of images
        val gridSize = when (albumArts.size) {
            in 1..3 -> 1
            in 4..6 -> 2
            else -> 3
        }

        // Set cell size based on grid size
        val cellSize = when (gridSize) {
            1 -> 1024 // Large for 1x1
            2 -> 768  // Medium for 2x2
            else -> 512 // Default for 3x3
        }

        val canvasSize = gridSize * cellSize

        for (str in albumArts) {
            val uri = str.toUri()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(cellSize, cellSize), null).let {
                    bitmaps.add(it)
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)?.let {
                        bitmaps.add(it)
                    }
                }
            }
            if (bitmaps.size == 9) break
        }

        if (bitmaps.isEmpty()) {
            callback.onLoadFailed(Exception("No album art found"))
            return
        }

        val result = createBitmap(canvasSize, canvasSize)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Draw only available images, leave remaining cells blank
        for (i in 0 until bitmaps.size) {
            val row = i / gridSize
            val col = i % gridSize
            val left = col * cellSize
            val top = row * cellSize
            val bmp = bitmaps[i]
            val scaled = bmp.scale(cellSize, cellSize)
            canvas.drawBitmap(scaled, left.toFloat(), top.toFloat(), paint)
            if (scaled != bmp) scaled.recycle()
        }

        callback.onDataReady(result)
    }

    override fun cleanup() {}
    override fun cancel() {}
    override fun getDataClass() = Bitmap::class.java
    override fun getDataSource() = DataSource.LOCAL
}