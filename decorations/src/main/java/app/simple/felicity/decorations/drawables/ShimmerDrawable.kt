package app.simple.felicity.decorations.drawables

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class ShimmerDrawable(
        private val baseColor: Int,
        private val highlightColor: Int,
        private val shimmerWidthFraction: Float,
        private val duration: Long,
        private val cornerRadius: Float = 0f // new property
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: LinearGradient? = null
    private val matrix = Matrix()
    private var animatedValue = 0f
    private var animator: ValueAnimator? = null

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        if (shader == null) createShader(bounds.width(), bounds.height())

        shader?.let {
            val dx = (bounds.width() * 2) * animatedValue - bounds.width()
            val dy = (bounds.height() * 2) * animatedValue - bounds.height()
            matrix.setTranslate(dx, dy)
            it.setLocalMatrix(matrix)
            paint.shader = it
            canvas.drawRoundRect(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat(),
                    cornerRadius,
                    cornerRadius,
                    paint
            )
        }
    }

    // Change signature to accept height
    private fun createShader(width: Int, height: Int) {
        // build gradient across diagonal; center highlight occupies shimmerWidthFraction of diagonal
        val highlight = shimmerWidthFraction.coerceIn(0.05f, 0.6f)
        val mid = 0.5f
        val colors = intArrayOf(baseColor, highlightColor, baseColor)
        val positions = floatArrayOf((mid - highlight / 2).coerceAtLeast(0f), mid, (mid + highlight / 2).coerceAtMost(1f))

        // Diagonal gradient: from top-left (0,0) to bottom-right (width,height)
        shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors, positions, Shader.TileMode.CLAMP
        )
    }

    fun start() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = this@ShimmerDrawable.duration
            interpolator = LinearInterpolator()
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                this@ShimmerDrawable.animatedValue = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }
}