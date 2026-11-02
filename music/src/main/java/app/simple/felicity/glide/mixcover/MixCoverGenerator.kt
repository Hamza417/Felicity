package app.simple.felicity.glide.mixcover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import kotlin.math.max
import kotlin.random.Random

object MixCoverGenerator {

    /**
     * Generates a dynamic, organic polygon-style collage of images.
     */
    fun createDynamicCollage(bitmaps: List<Bitmap>, width: Int, height: Int): Bitmap {
        val output = createBitmap(width, height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        // Start with one large rectangle filling the entire canvas
        val paths = mutableListOf(Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        })

        // Binary Space Partitioning: continually split the largest polygon with a random line
        while (paths.size < bitmaps.size) {
            // Find the polygon with the largest bounding area to split
            val target = paths.maxByOrNull { path ->
                val b = RectF()
                path.computeBounds(b, true)
                b.width() * b.height()
            } ?: break

            paths.remove(target)

            val bounds = RectF()
            target.computeBounds(bounds, true)

            // Pick a random center point inside the bounds to anchor the splitting line.
            // Perturb it slightly off-center (±30% of size) to keep it organic but avoid tiny slivers.
            val cx = bounds.centerX() + (Random.nextFloat() - 0.5f) * bounds.width() * 0.6f
            val cy = bounds.centerY() + (Random.nextFloat() - 0.5f) * bounds.height() * 0.6f

            // Random split angle
            val angle = Random.nextFloat() * 360f

            // Create a huge half-plane to intersect with the target path
            val size = max(width, height) * 4f

            val half1 = Path().apply {
                addRect(-size, 0f, size, size, Path.Direction.CW)
            }
            val matrix1 = Matrix()
            matrix1.postRotate(angle)
            matrix1.postTranslate(cx, cy)
            half1.transform(matrix1)

            val half2 = Path().apply {
                addRect(-size, -size, size, 0f, Path.Direction.CW)
            }
            val matrix2 = Matrix()
            matrix2.postRotate(angle)
            matrix2.postTranslate(cx, cy)
            half2.transform(matrix2)

            // Intersect the target path with both half-planes to split it into two new polygons
            val p1 = Path().apply { op(target, half1, Path.Op.INTERSECT) }
            val p2 = Path().apply { op(target, half2, Path.Op.INTERSECT) }

            paths.add(p1)
            paths.add(p2)
        }

        // Draw each bitmap perfectly scaled into its organic bounding path
        paths.forEachIndexed { index, path ->
            if (index >= bitmaps.size) return@forEachIndexed

            val bmp = bitmaps[index]

            canvas.withClip(path) {
                val bounds = RectF()
                path.computeBounds(bounds, true)

                val scale = max(bounds.width() / bmp.width, bounds.height() / bmp.height)
                val matrix = Matrix()

                matrix.postScale(scale, scale)
                matrix.postTranslate(
                        bounds.centerX() - (bmp.width * scale) / 2f,
                        bounds.centerY() - (bmp.height * scale) / 2f
                )

                drawBitmap(bmp, matrix, paint)
            }

            // Draw clearing stroke to create the empty gaps between polygons
            canvas.drawPath(path, clearPaint)
        }

        return output
    }
}
