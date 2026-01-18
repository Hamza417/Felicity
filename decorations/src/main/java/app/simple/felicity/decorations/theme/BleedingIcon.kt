package app.simple.felicity.decorations.theme

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.use
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import app.simple.felicity.decoration.R
import app.simple.felicity.preferences.AccessibilityPreferences
import app.simple.felicity.preferences.AppearancePreferences
import app.simple.felicity.shared.helpers.ImageHelper
import app.simple.felicity.theme.interfaces.ThemeChangedListener
import app.simple.felicity.theme.managers.ThemeManager
import app.simple.felicity.theme.models.Accent
import app.simple.felicity.theme.themes.Theme
import java.util.Objects

class BleedingIcon : AppCompatImageView, ThemeChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {
    // Tint behavior mirrors ThemeIcon
    private var tintMode: Int = 0
    private var tintAnimator: ValueAnimator? = null

    // Bleed effect
    private var bleedRadiusPx: Float = 0f
    private var bleedIntensity: Float = 0.88f // 0..1, controls alpha of bleed
    private val bleedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private var alphaMask: Bitmap? = null
    private val tempCanvasBitmapLock = Any()
    private var blurMaskFilter: BlurMaskFilter? = null
    private var currentMaskRadius: Float = -1f
    private var isViewReady = false

    private val drawMatrix = Matrix()

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(
            context, attrs, defStyleAttr
    ) {
        init(attrs)
    }

    @SuppressLint("CustomViewStyleable")
    private fun init(attrs: AttributeSet?) {
        if (isInEditMode) return

        // Default bleed radius ~3dp (very subtle)
        bleedRadiusPx = 3f * resources.displayMetrics.density

        // Read common tintType from ThemeIcon styleable for drop-in compatibility
        context.obtainStyledAttributes(attrs, R.styleable.ThemeIcon).use { a ->
            tintMode = a.getInteger(R.styleable.ThemeIcon_tintType, 0)
        }

        // Use software layer for BlurMaskFilter to work consistently
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        setTintColor(tintMode, animate = false)
        isViewReady = true
    }

    private fun computeTintForMode(mode: Int): Int {
        return when (mode) {
            0 -> ThemeManager.theme.iconTheme.regularIconColor
            1 -> ThemeManager.theme.iconTheme.secondaryIconColor
            2 -> ThemeManager.accent.primaryAccentColor
            3 -> imageTintList?.defaultColor ?: ThemeManager.theme.iconTheme.regularIconColor // custom
            else -> ThemeManager.theme.iconTheme.regularIconColor
        }
    }

    private fun setTint(endColor: Int, animate: Boolean) {
        val animationsAllowed = animate && !AccessibilityPreferences.isAnimationReduced()
        if (animationsAllowed) {
            val startColor = imageTintList?.defaultColor ?: computeTintForMode(tintMode)
            if (tintAnimator?.isRunning == true) tintAnimator?.cancel()
            tintAnimator = ValueAnimator.ofArgb(startColor, endColor).apply {
                duration = resources.getInteger(R.integer.animation_duration).toLong()
                interpolator = LinearOutSlowInInterpolator()
                addUpdateListener {
                    val c = it.animatedValue as Int
                    imageTintList = ColorStateList.valueOf(c)
                    invalidate() // also refresh bleed color
                }
                start()
            }
        } else {
            imageTintList = ColorStateList.valueOf(endColor)
            invalidate()
        }
    }

    private fun setTintColor(tintMode: Int, animate: Boolean) {
        val endColor = computeTintForMode(tintMode)
        setTint(endColor, animate)
    }

    fun setIcon(resId: Int, animate: Boolean) {
        if (animate && !AccessibilityPreferences.isAnimationReduced()) {
            ImageHelper.loadImage(resId, this, 0)
        } else {
            setImageResource(resId)
        }
    }

    private fun rebuildAlphaMaskIfNeeded() {
        val w = width
        val h = height
        if (drawable == null) return
        if (w <= 0 || h <= 0) return

        synchronized(tempCanvasBitmapLock) {
            // Build a temporary bitmap of what ImageView would draw, then extract alpha
            var content: Bitmap? = null
            try {
                content = createBitmap(w, h)
                val c = Canvas(content)
                // Draw the image as ImageView would. This respects scaleType and imageMatrix.
                c.withSave {
                    // Draw only the image content (not background), so call ImageView's draw path
                    // Using the drawable directly with its bounds is tricky; instead, ask ImageView to render into our canvas.
                    // Temporarily draw the image onto the temp canvas.
                    drawIntoCanvas(c)
                }

                val alpha = content.extractAlpha()
                // Replace previous mask if dimensions differ
                if (alphaMask?.width != alpha.width || alphaMask?.height != alpha.height) {
                    alphaMask?.recycle()
                    alphaMask = null
                }
                alphaMask?.recycle()
                alphaMask = alpha
            } catch (_: Throwable) {
                // Ignore mask failures; just skip bleed
                alphaMask = null
            } finally {
                content?.recycle()
            }
        }
    }

    private fun imageContentSize(): Pair<Int, Int> {
        val d = drawable ?: return 0 to 0
        val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else (width - paddingLeft - paddingRight)
        val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else (height - paddingTop - paddingBottom)
        return iw to ih
    }

    private fun computeDrawMatrixForContent(availW: Int, availH: Int, dwidth: Int, dheight: Int, st: ScaleType): Matrix {
        drawMatrix.reset()
        if (dwidth <= 0 || dheight <= 0) return drawMatrix
        val vw = availW.toFloat()
        val vh = availH.toFloat()
        val dw = dwidth.toFloat()
        val dh = dheight.toFloat()

        when (st) {
            ScaleType.CENTER -> {
                val dx = (vw - dw) * 0.5f
                val dy = (vh - dh) * 0.5f
                drawMatrix.postTranslate(dx, dy)
            }
            ScaleType.CENTER_CROP -> {
                val scale: Float
                var dx = 0f
                var dy = 0f
                if (dw * vh > vw * dh) {
                    scale = vh / dh
                    dx = (vw - dw * scale) * 0.5f
                } else {
                    scale = vw / dw
                    dy = (vh - dh * scale) * 0.5f
                }
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
            ScaleType.CENTER_INSIDE -> {
                val scale = minOf(1f, minOf(vw / dw, vh / dh))
                val dx = (vw - dw * scale) * 0.5f
                val dy = (vh - dh * scale) * 0.5f
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
            ScaleType.FIT_CENTER -> {
                val scale = minOf(vw / dw, vh / dh)
                val dx = (vw - dw * scale) * 0.5f
                val dy = (vh - dh * scale) * 0.5f
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
            ScaleType.FIT_START -> {
                val scale = minOf(vw / dw, vh / dh)
                drawMatrix.postScale(scale, scale)
                // top-left (no extra translate)
            }
            ScaleType.FIT_END -> {
                val scale = minOf(vw / dw, vh / dh)
                val dx = (vw - dw * scale)
                val dy = (vh - dh * scale)
                drawMatrix.postScale(scale, scale)
                drawMatrix.postTranslate(dx, dy)
            }
            else -> {
                // FIT_XY handled separately by setBounds to content rect
            }
        }
        return drawMatrix
    }

    // Draw only the image into the provided canvas (mirrors ImageView rules)
    private fun drawIntoCanvas(c: Canvas) {
        val d = drawable ?: return
        val pl = paddingLeft
        val pt = paddingTop
        val pr = paddingRight
        val pb = paddingBottom
        val availW = (width - pl - pr).coerceAtLeast(0)
        val availH = (height - pt - pb).coerceAtLeast(0)
        if (availW == 0 || availH == 0) return

        val iw = if (d.intrinsicWidth > 0) d.intrinsicWidth else availW
        val ih = if (d.intrinsicHeight > 0) d.intrinsicHeight else availH
        c.withTranslation(pl.toFloat(), pt.toFloat()) {
            val st = scaleType ?: ScaleType.FIT_CENTER
            if (st == ScaleType.FIT_XY) {
                d.setBounds(0, 0, availW, availH)
                d.draw(c)
            } else {
                d.setBounds(0, 0, iw, ih)
                val m = computeDrawMatrixForContent(availW, availH, iw, ih, st)
                c.concat(m)
                d.draw(c)
            }
        }
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        // Try to keep the alpha mask up to date when size or drawable changed
        if (alphaMask == null || alphaMask?.width != width || alphaMask?.height != height) {
            rebuildAlphaMaskIfNeeded()
        }

        // Draw bleed first
        alphaMask?.let { mask ->
            val baseTint = imageTintList?.defaultColor ?: computeTintForMode(tintMode)
            val bleedColor = applyAlpha(baseTint, (255f * bleedIntensity).toInt().coerceIn(0, 255))
            bleedPaint.color = bleedColor
            if (currentMaskRadius != bleedRadiusPx) {
                currentMaskRadius = bleedRadiusPx
                blurMaskFilter = if (currentMaskRadius > 0f) {
                    BlurMaskFilter(currentMaskRadius, BlurMaskFilter.Blur.OUTER)
                } else {
                    null
                }
            }
            bleedPaint.maskFilter = blurMaskFilter
            canvas.drawBitmap(mask, 0f, 0f, bleedPaint)
        }

        // Then draw the crisp icon
        super.onDraw(canvas)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        // During base class construction, this may be invoked before our fields are initialized
        if (!isViewReady) {
            super.setImageDrawable(drawable)
            return
        }
        super.setImageDrawable(drawable)
        recycleMaskAndInvalidate()
    }

    override fun setImageResource(resId: Int) {
        if (!isViewReady) {
            super.setImageResource(resId)
            return
        }
        super.setImageResource(resId)
        recycleMaskAndInvalidate()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        if (!isViewReady) {
            super.setImageBitmap(bm)
            return
        }
        super.setImageBitmap(bm)
        recycleMaskAndInvalidate()
    }

    private fun recycleMaskAndInvalidate() {
        // Skip if view isn't fully initialized yet (constructor phase)
        if (!isViewReady) return
        synchronized(tempCanvasBitmapLock) {
            alphaMask?.recycle()
            alphaMask = null
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recycleMaskAndInvalidate()
    }

    fun setBleedRadiusDp(dp: Float) {
        bleedRadiusPx = (dp * resources.displayMetrics.density).coerceAtLeast(0f)
        invalidate()
    }

    fun setBleedIntensity(intensity: Float) {
        bleedIntensity = intensity.coerceIn(0f, 1f)
        invalidate()
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return
        app.simple.felicity.manager.SharedPreferences.registerListener(this)
        ThemeManager.addListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        app.simple.felicity.manager.SharedPreferences.unregisterListener(this)
        ThemeManager.removeListener(this)
        tintAnimator?.cancel()
        tintAnimator = null
        synchronized(tempCanvasBitmapLock) {
            alphaMask?.recycle()
            alphaMask = null
        }
    }

    override fun onThemeChanged(theme: Theme, animate: Boolean) {
        setTintColor(tintMode, animate)
    }

    override fun onAccentChanged(accent: Accent) {
        super.onAccentChanged(accent)
        if (tintMode == 2) {
            setTintColor(tintMode, true)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            setTintColor(tintMode, true)
        }
    }
}