@file:Suppress("RemoveRedundantQualifierName")

package app.simple.felicity.decorations.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Full-screen transparent overlay that performs a true MaterialContainerTransform-style morph.
 *
 * Architecture:
 *  - The overlay itself is MATCH_PARENT and transparent — it never intercepts touches.
 *  - [morphHost] is an inner FrameLayout sized/positioned to the current morph rect.
 *    It carries the animated background, clipToOutline, and all content children.
 *  - A [shadowHost] sits behind [morphHost] at the same rect and owns elevation +
 *    a matching rounded outline so the system shadow is always correctly shaped and cast.
 *  - [anchorBitmap] is an optional snapshot of the origin view drawn on top of the
 *    morphHost background (fading out) so the card appears to *convert* rather than pop.
 *  - Content children are scaled from 0→1 pivoted at the anchor center so they grow
 *    out of the origin shape rather than just fading in at full size.
 */
class MorphLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // ── Inner views ──────────────────────────────────────────────────────────────

    /** Carries elevation + outline so the system casts a correctly-shaped shadow. */
    val shadowHost: FrameLayout

    /** Carries the background shape + clips content to the rounded rect. */
    val morphHost: FrameLayout

    // ── Shape ────────────────────────────────────────────────────────────────────

    private val shapeDrawable = MaterialShapeDrawable()
    private var currentCornerRadius: Float = 0f

    // ── Morph rect (overlay-local coordinates) ───────────────────────────────────

    private var morphLeft: Int = 0
    private var morphTop: Int = 0
    private var morphRight: Int = 0
    private var morphBottom: Int = 0

    // ── Anchor cross-fade bitmap ─────────────────────────────────────────────────

    /** Snapshot of the origin view — drawn on top of the background fading out to 0. */
    var anchorBitmap: Bitmap? = null

    /** 0 → anchor fully visible, 1 → anchor fully gone. Driven externally. */
    var anchorBitmapAlpha: Float = 0f

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bitmapSrcRect = Rect()
    private val bitmapDstRect = RectF()

    // ── Content scale-up from anchor pivot ───────────────────────────────────────

    /** Scale applied to all content children — driven from 0→1 by the animator. */
    @Suppress("unused")
    var contentScale: Float = 0f
        set(value) {
            field = value
            for (i in 0 until morphHost.childCount) {
                morphHost.getChildAt(i).scaleX = value
                morphHost.getChildAt(i).scaleY = value
            }
        }

    /** Alpha applied to all content children — driven by the animator. */
    var contentAlpha: Float = 0f
        set(value) {
            field = value
            for (i in 0 until morphHost.childCount) {
                morphHost.getChildAt(i).alpha = value
            }
        }

    @ColorInt
    private var currentColor: Int = Color.WHITE

    // ── Init ─────────────────────────────────────────────────────────────────────

    init {
        background = null
        setWillNotDraw(false)      // we draw the anchor bitmap cross-fade ourselves
        isClickable = false
        isFocusable = false

        // Shadow host — behind the morph card, same rect, just for system shadow
        shadowHost = FrameLayout(context).apply {
            background = MaterialShapeDrawable()   // opaque so shadow renders
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
                }
            }
            clipToOutline = false
        }

        // Morph host — clips children + owns visible background
        morphHost = FrameLayout(context).apply {
            background = shapeDrawable
            clipChildren = true
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
                }
            }
        }

        // Add shadow first (behind), then morph content on top
        super.addView(shadowHost, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        super.addView(morphHost, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    // ── Route external addView calls into morphHost ───────────────────────────────

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child === shadowHost || child === morphHost) {
            super.addView(child, index, params)
        } else {
            val lp = params as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(params.width, params.height)
            morphHost.addView(child, lp)
            // Apply pending values so pre-set contentAlpha/contentScale take effect
            // even though the child didn't exist when the setters first ran
            child.alpha = contentAlpha
            child.scaleX = contentScale
            child.scaleY = contentScale
        }
    }

    // ── Forward elevation to shadowHost ──────────────────────────────────────────

    override fun setElevation(elevation: Float) {
        shadowHost.elevation = elevation
        // morphHost sits directly on top — no elevation needed (would double shadow)
        morphHost.elevation = 0f
    }

    override fun getElevation(): Float = shadowHost.elevation

    override fun setOutlineAmbientShadowColor(color: Int) {
        shadowHost.outlineAmbientShadowColor = color
    }

    override fun setOutlineSpotShadowColor(color: Int) {
        shadowHost.outlineSpotShadowColor = color
    }

    // ── Layout ────────────────────────────────────────────────────────────────────

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = morphRight - morphLeft
        val h = morphBottom - morphTop
        shadowHost.layout(morphLeft, morphTop, morphRight, morphBottom)
        morphHost.layout(morphLeft, morphTop, morphRight, morphBottom)
        for (i in 0 until morphHost.childCount) {
            morphHost.getChildAt(i).layout(0, 0, w, h)
        }
    }

    // ── Draw anchor bitmap cross-fade on top of morphHost content ───────────────

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)   // shadowHost + morphHost (with content) drawn first

        val bmp = anchorBitmap ?: return
        // anchorBitmapAlpha 0→1 means bitmap fades from fully visible to fully gone
        val alpha = ((1f - anchorBitmapAlpha) * 255f).toInt().coerceIn(0, 255)
        if (alpha == 0) return

        val left = morphLeft.toFloat()
        val top = morphTop.toFloat()
        val right = morphRight.toFloat()
        val bottom = morphBottom.toFloat()
        val clipW = right - left
        val clipH = bottom - top
        if (clipW <= 0 || clipH <= 0) return

        bitmapSrcRect.set(0, 0, bmp.width, bmp.height)
        bitmapDstRect.set(left, top, right, bottom)
        bitmapPaint.alpha = alpha

        canvas.save()
        // Clip the bitmap draw to the rounded morph rect
        canvas.clipPath(android.graphics.Path().apply {
            addRoundRect(left, top, right, bottom,
                         currentCornerRadius, currentCornerRadius,
                         android.graphics.Path.Direction.CW)
        })
        canvas.drawBitmap(bmp, bitmapSrcRect, bitmapDstRect, bitmapPaint)
        canvas.restore()
    }

    // ── Core morph API ────────────────────────────────────────────────────────────

    fun setMorphState(
            left: Int, top: Int, right: Int, bottom: Int,
            cornerRadius: Float,
            @ColorInt color: Int
    ) {
        val w = right - left
        val h = bottom - top

        morphLeft = left
        morphTop = top
        morphRight = right
        morphBottom = bottom
        currentCornerRadius = cornerRadius
        currentColor = color

        // Update background shape in-place
        shapeDrawable.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
            .build()
        shapeDrawable.fillColor = ColorStateList.valueOf(color)
        shapeDrawable.setBounds(0, 0, w, h)

        // Sync shadow host background shape too (drives shadow shape)
        (shadowHost.background as? MaterialShapeDrawable)?.let { sd ->
            sd.shapeAppearanceModel = ShapeAppearanceModel.builder()
                .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                .build()
            sd.fillColor = ColorStateList.valueOf(color)
            sd.setBounds(0, 0, w, h)
        }

        shadowHost.layout(left, top, right, bottom)
        morphHost.layout(left, top, right, bottom)
        for (i in 0 until morphHost.childCount) {
            morphHost.getChildAt(i).layout(0, 0, w, h)
        }

        shadowHost.invalidateOutline()
        morphHost.invalidateOutline()
        invalidate()
    }

    fun setMorphState(rect: Rect, cornerRadius: Float, @ColorInt color: Int) {
        setMorphState(rect.left, rect.top, rect.right, rect.bottom, cornerRadius, color)
    }

    /** Release bitmap resources. Call when popup is fully dismissed. */
    fun clearAnchorBitmap() {
        anchorBitmap?.recycle()
        anchorBitmap = null
    }
}
