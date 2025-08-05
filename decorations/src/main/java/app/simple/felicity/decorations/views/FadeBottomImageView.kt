package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView

class FadeBottomImageView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : AppCompatImageView(context, attrs, defStyle) {

    private val paint = Paint()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val height = height.toFloat()
        val shader = LinearGradient(
                0f, height * 0.7f, 0f, height,
                0x00FFFFFF, 0xFF000000.toInt(), Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(0f, height * 0.7f, width.toFloat(), height, paint)
        paint.xfermode = null
    }
}