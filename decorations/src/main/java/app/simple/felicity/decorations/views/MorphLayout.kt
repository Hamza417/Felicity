@file:Suppress("RemoveRedundantQualifierName")

package app.simple.felicity.decorations.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Full-screen transparent overlay performing a MaterialContainerTransform morph.
 *
 *  - [morphHost] is a card-sized FrameLayout manually positioned to the morph rect
 *    each frame. It owns the background, shadow (elevation), outline clipping.
 *  - The overlay itself draws the scrim dim + the anchor bitmap cross-fade on top.
 *  - Touch events outside [morphHost] bounds are passed to [onOutsideTouchListener]
 *    so the caller can dismiss.
 */
class MorphLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── Card host ────────────────────────────────────────────────────────────────
    val morphHost: FrameLayout

    // ── Shape state ──────────────────────────────────────────────────────────────
    private val shapeDrawable = MaterialShapeDrawable()
    private var currentCornerRadius = 0f

    // ── Morph rect (overlay-local coords) ────────────────────────────────────────
    private var morphLeft = 0
    private var morphTop = 0
    private var morphRight = 0
    private var morphBottom = 0

    // ── Scrim ────────────────────────────────────────────────────────────────────
    var scrimAlpha: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f); invalidate()
        }

    private val scrimPaint = Paint().apply { color = Color.BLACK }

    // ── Anchor cross-fade ────────────────────────────────────────────────────────
    var anchorBitmap: Bitmap? = null
    var anchorBitmapAlpha: Float = 0f          // 0 = bitmap opaque, 1 = bitmap gone
        set(value) {
            field = value.coerceIn(0f, 1f); invalidate()
        }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapSrcRect = Rect()
    private val bitmapDstRect = RectF()
    private val bitmapClip = Path()

    // ── Content reveal ───────────────────────────────────────────────────────────
    var contentAlpha: Float = 1f
        set(value) {
            field = value
            for (i in 0 until morphHost.childCount) morphHost.getChildAt(i).alpha = value
        }

    var contentScale: Float = 1f
        set(value) {
            field = value
            for (i in 0 until morphHost.childCount) {
                morphHost.getChildAt(i).scaleX = value
                morphHost.getChildAt(i).scaleY = value
            }
        }

    // ── Outside-touch dismiss callback ───────────────────────────────────────────
    var onOutsideTouchListener: (() -> Unit)? = null

    // ── Init ─────────────────────────────────────────────────────────────────────
    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)
        // The overlay itself must NOT be clickable/focusable — we handle touch manually
        isClickable = false
        isFocusable = false

        morphHost = FrameLayout(context).apply {
            background = shapeDrawable
            clipChildren = true
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
                }
            }
            // Card intercepts its own touches so scroll works, but does NOT consume
            // touches that fall outside the card bounds (handled by overlay below).
            isClickable = true
        }

        super.addView(morphHost, LayoutParams(0, 0))  // size driven entirely by layout()
    }

    // ── addView: route content into morphHost, stamp pending alpha/scale ─────────
    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child === morphHost) {
            super.addView(child, index, params)
        } else {
            val lp = params as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(params.width, params.height)
            morphHost.addView(child, lp)
            child.alpha = contentAlpha
            child.scaleX = contentScale
            child.scaleY = contentScale
        }
    }

    // ── Elevation → morphHost ────────────────────────────────────────────────────
    override fun setElevation(elevation: Float) {
        morphHost.elevation = elevation
    }

    override fun getElevation(): Float = morphHost.elevation
    override fun setOutlineAmbientShadowColor(color: Int) {
        morphHost.outlineAmbientShadowColor = color
    }

    override fun setOutlineSpotShadowColor(color: Int) {
        morphHost.outlineSpotShadowColor = color
    }

    // ── Layout ───────────────────────────────────────────────────────────────────
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Always use the stored morph rect — never the MATCH_PARENT overlay bounds.
        layoutMorphHost()
    }

    private fun layoutMorphHost() {
        val w = (morphRight - morphLeft).coerceAtLeast(0)
        val h = (morphBottom - morphTop).coerceAtLeast(0)
        morphHost.layout(morphLeft, morphTop, morphRight, morphBottom)
        // Fill every content child to the full card size
        for (i in 0 until morphHost.childCount) {
            morphHost.getChildAt(i).layout(0, 0, w, h)
        }
    }

    // ── Touch: outside card = dismiss ─────────────────────────────────────────────
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // If touch is outside the card rect, intercept it so we can dismiss
        if (ev.action == MotionEvent.ACTION_DOWN) {
            if (ev.x < morphLeft || ev.x > morphRight ||
                    ev.y < morphTop || ev.y > morphBottom) {
                return true
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            onOutsideTouchListener?.invoke()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        onOutsideTouchListener?.invoke()
        return super.performClick()
    }

    // ── Draw: scrim behind card, anchor bitmap cross-fade on top of card ──────────
    override fun draw(canvas: Canvas) {
        // 1. Scrim — drawn before super.draw so it appears behind the card
        if (scrimAlpha > 0f) {
            scrimPaint.alpha = (scrimAlpha * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        }

        // 2. Card + content children
        super.draw(canvas)

        // 3. Anchor bitmap cross-fade — drawn ON TOP of card content, fading out
        drawAnchorBitmap(canvas)
    }

    private fun drawAnchorBitmap(canvas: Canvas) {
        val bmp = anchorBitmap ?: return
        val alpha = ((1f - anchorBitmapAlpha) * 255f).toInt().coerceIn(0, 255)
        if (alpha == 0) return

        val l = morphLeft.toFloat();
        val t = morphTop.toFloat()
        val r = morphRight.toFloat();
        val b = morphBottom.toFloat()
        if (r <= l || b <= t) return

        bitmapSrcRect.set(0, 0, bmp.width, bmp.height)
        bitmapDstRect.set(l, t, r, b)
        bitmapPaint.alpha = alpha

        bitmapClip.rewind()
        bitmapClip.addRoundRect(l, t, r, b, currentCornerRadius, currentCornerRadius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(bitmapClip)
        canvas.drawBitmap(bmp, bitmapSrcRect, bitmapDstRect, bitmapPaint)
        canvas.restore()
    }

    // ── Core morph API ────────────────────────────────────────────────────────────
    fun setMorphState(
            left: Int, top: Int, right: Int, bottom: Int,
            cornerRadius: Float,
            @ColorInt color: Int
    ) {
        val w = (right - left).coerceAtLeast(1)
        val h = (bottom - top).coerceAtLeast(1)

        morphLeft = left; morphTop = top
        morphRight = right; morphBottom = bottom
        currentCornerRadius = cornerRadius

        shapeDrawable.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius).build()
        shapeDrawable.fillColor = ColorStateList.valueOf(color)
        shapeDrawable.setBounds(0, 0, w, h)

        layoutMorphHost()
        morphHost.invalidateOutline()
        invalidate()
    }

    fun setMorphState(rect: Rect, cornerRadius: Float, @ColorInt color: Int) =
        setMorphState(rect.left, rect.top, rect.right, rect.bottom, cornerRadius, color)

    fun clearAnchorBitmap() {
        anchorBitmap?.recycle()
        anchorBitmap = null
        invalidate()
    }
}
