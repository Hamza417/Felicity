package app.simple.felicity.extensions.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import app.simple.felicity.decorations.ripple.DynamicRippleConstraintLayout
import app.simple.felicity.repository.listeners.MediaStateListener
import app.simple.felicity.repository.models.Audio

/**
 * A [DynamicRippleConstraintLayout] that automatically tracks the currently playing
 * song and any active selection state, drawing visual indicators directly on the canvas
 * so no extra child views are needed.
 *
 * All drawing and state logic lives in [MediaAwareDelegate], which is shared with
 * [MediaAwareRippleLinearLayout]. This class is just the ConstraintLayout-flavored
 * shell that hooks the View lifecycle into the delegate.
 *
 * See [MediaAwareDelegate] for a full explanation of list mode vs. grid mode behavior.
 *
 * @author Hamza417
 */
class MediaAwareRippleConstraintLayout @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : DynamicRippleConstraintLayout(context, attrs), MediaStateListener {

    private val delegate = MediaAwareDelegate(this, context)

    var enableDragHandle: Boolean
        get() = delegate.enableDragHandle
        set(value) {
            delegate.enableDragHandle = value
        }

    var enableGridMode: Boolean
        get() = delegate.enableGridMode
        set(value) {
            delegate.enableGridMode = value
        }

    init {
        setWillNotDraw(false)
    }

    fun setAudioID(audioID: Long) = delegate.setAudioID(audioID)

    fun isDragHandleRegion(x: Float) = delegate.isDragHandleRegion(x)

    override fun onAudioChange(audio: Audio?) = delegate.onAudioChange(audio)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        delegate.onSizeChanged(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        delegate.onLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        delegate.onDraw(canvas)
    }

    override fun dispatchDraw(canvas: Canvas) {
        delegate.dispatchDraw(canvas) { super.dispatchDraw(canvas) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        delegate.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        delegate.onDetachedFromWindow()
    }
}
