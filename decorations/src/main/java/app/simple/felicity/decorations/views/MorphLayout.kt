@file:Suppress("RemoveRedundantQualifierName")

package app.simple.felicity.decorations.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
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
 * A FrameLayout that performs a true rect→rect morph each animation frame.
 *
 * Key design decisions:
 *  - [setMorphState] calls [layout] directly to reposition/resize without
 *    triggering a full measure pass — keeping 60fps morph cheap.
 *  - [onLayout] always fills every child to the current view bounds, so
 *    children with MATCH_PARENT always resolve correctly even though we
 *    bypass the normal measure/layout cycle.
 *  - Children are added with [FrameLayout.LayoutParams] so that
 *    [measureChildWithMargins] (called by FrameLayout.onMeasure) never
 *    receives a plain [ViewGroup.LayoutParams] — which would crash.
 *  - Background is a single [MaterialShapeDrawable] updated in-place each
 *    frame (corner radius + fill color), zero allocations per frame.
 *  - [clipToOutline] + live [ViewOutlineProvider] clip children to the
 *    animated rounded rect and also drive the system shadow shape.
 *  - [elevation] is driven externally by [SharedScrollViewPopup] to create
 *    the "lifting out of the surface" depth arc.
 */
class MorphLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val shapeDrawable = MaterialShapeDrawable()

    private var currentCornerRadius: Float = 0f

    @ColorInt
    private var currentColor: Int = Color.WHITE

    /** Alpha applied to all direct children simultaneously. */
    var contentAlpha: Float = 0f
        set(value) {
            field = value
            for (i in 0 until childCount) {
                getChildAt(i).alpha = value
            }
        }

    init {
        background = shapeDrawable
        clipChildren = true
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, currentCornerRadius)
            }
        }
        visibility = INVISIBLE
    }

    // ── Layout params: always FrameLayout.LayoutParams so measureChildWithMargins ──
    // never receives a plain ViewGroup.LayoutParams (which has no margins and would
    // crash with ClassCastException to MarginLayoutParams inside FrameLayout.onMeasure)

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): ViewGroup.LayoutParams =
        lp as? LayoutParams ?: LayoutParams(lp.width, lp.height)

    override fun checkLayoutParams(p: ViewGroup.LayoutParams): Boolean = p is LayoutParams

    // ── Children always fill current bounds ──────────────────────────────────────
    // Called after every layout() call. We force all children to exactly fill our
    // current l/t/r/b so MATCH_PARENT resolves correctly without a measure pass.

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for (i in 0 until childCount) {
            getChildAt(i).layout(0, 0, w, h)
        }
    }

    // ── Core morph API ───────────────────────────────────────────────────────────

    /**
     * Drive one animation frame. Repositions this view in its parent, updates
     * the background shape and fill color — all without triggering a full
     * measure/layout traversal.
     */
    fun setMorphState(
            left: Int, top: Int, right: Int, bottom: Int,
            cornerRadius: Float,
            @ColorInt color: Int
    ) {
        val w = right - left
        val h = bottom - top

        currentCornerRadius = cornerRadius
        currentColor = color

        // Rebuild shape appearance in-place
        shapeDrawable.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
            .build()
        shapeDrawable.fillColor = ColorStateList.valueOf(color)
        shapeDrawable.setBounds(0, 0, w, h)

        // Reposition + resize — triggers onLayout which fills children
        layout(left, top, right, bottom)

        // Outline drives both clipToOutline clip AND the system drop-shadow shape
        invalidateOutline()
    }

    fun setMorphState(rect: Rect, cornerRadius: Float, @ColorInt color: Int) {
        setMorphState(rect.left, rect.top, rect.right, rect.bottom, cornerRadius, color)
    }
}
