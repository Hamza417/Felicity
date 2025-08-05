package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ReflectionView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var sourceView: RecyclerView? = null
    var reflectionOffsetY: Int = 0 // Positive values move reflection up (closer to content)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val src = sourceView ?: return

        if (width == 0 || height == 0) return

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        // Offset to capture the bottom part of the RecyclerView, plus reflectionOffsetY
        val offset = (-(src.height - height) + reflectionOffsetY).coerceAtMost(0).toFloat()
        c.translate(0f, offset)
        src.draw(c)

        val matrix = Matrix().apply { preScale(1f, -1f) }
        val reflected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)

        val paint = Paint()
        val shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                0xFFFFFFFF.toInt(), 0x00000000, Shader.TileMode.CLAMP
        )

        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

        canvas.drawBitmap(reflected, 0f, 0f, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        bitmap.recycle()
        reflected.recycle()
    }
}