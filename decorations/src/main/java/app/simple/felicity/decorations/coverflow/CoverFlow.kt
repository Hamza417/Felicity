package app.simple.felicity.decorations.coverflow

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.OverScroller
import app.simple.felicity.decorations.coverflow.CoverFlowRenderer.ScrollListener
import app.simple.felicity.preferences.CarouselPreferences
import app.simple.felicity.preferences.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.preferences.SharedPreferences.unregisterSharedPreferenceChangeListener

class CoverFlow @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs),
    Choreographer.FrameCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val renderer: CoverFlowRenderer
    private val gestureDetector: GestureDetector
    private val scroller: OverScroller
    private val choreographer = Choreographer.getInstance()

    private var animating = false
    private var lastFlingCoord = 0 // generic axis (always X of scroller)
    private var downY = 0f
    private var coverClickListener: OnCoverClickListener? = null
    private var verticalMode = false

    interface OnCoverClickListener {
        fun onCenteredCoverClick(index: Int, uri: Uri?) {}
        fun onSideCoverSelected(index: Int, uri: Uri?) {}
    }

    fun setOnCoverClickListener(listener: OnCoverClickListener?) {
        coverClickListener = listener
    }

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true) // or setZOrderMediaOverlay(true) if needed
        renderer = CoverFlowRenderer(this, context.applicationContext)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scroller = OverScroller(context)

        registerSharedPreferenceChangeListener()

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.abortAnimation()
                lastFlingCoord = (renderer.scrollOffset * 1000).toInt()
                downY = e.y
                ensureAnimating()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val dim = if (verticalMode) height else width
                if (dim > 0) {
                    // For vertical mode use raw distanceY so upward movement (distanceY > 0) advances forward
                    val dist = if (verticalMode) distanceY else distanceX
                    renderer.scrollBy(dist / (dim * 0.3f))
                }
                requestRender()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dim = if (verticalMode) height else width
                if (dim <= 0) return true
                // For vertical mode use raw velocityY so upward fling (velocityY < 0) advances forward
                val velAxis = if (verticalMode) velocityY else velocityX
                val velocityItemsPerSec = velAxis / (dim * 0.5f)
                val start = (renderer.scrollOffset * 1000).toInt()
                val vel = (velocityItemsPerSec * 1000).toInt()
                scroller.fling(
                        start, 0,
                        -vel, 0,
                        Int.MIN_VALUE / 4, Int.MAX_VALUE / 4,
                        0, 0
                )
                lastFlingCoord = start
                ensureAnimating()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val tapped = if (verticalMode) renderer.pickIndexAtScreenY(e.y) else renderer.pickIndexAtScreenX(e.x)
                if (tapped != null) {
                    val centered = renderer.centeredIndex()
                    if (tapped == centered) {
                        coverClickListener?.onCenteredCoverClick(tapped, renderer.getUriAt(tapped))
                    } else {
                        queueEvent { renderer.scrollToIndex(tapped, smooth = true) }
                        coverClickListener?.onSideCoverSelected(tapped, renderer.getUriAt(tapped))
                    }
                    requestRender()
                    return true
                }
                renderer.snapToNearest()
                requestRender()
                return true
            }
        })
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val newVertical = h > w
        if (newVertical != verticalMode) {
            verticalMode = newVertical
            queueEvent { renderer.setVerticalOrientation(verticalMode) }
        }
    }

    fun setUris(uris: List<Uri>) {
        queueEvent { renderer.setUris(uris) }
        requestRender()
    }

    fun reloadTextures() {
        queueEvent { renderer.forceReloadAll() }
        requestRender()
    }

    // Programmatic scroll APIs
    fun setScrollOffset(offset: Float, smooth: Boolean = false) {
        queueEvent { renderer.setScrollOffset(offset, smooth) }
        requestRender()
    }

    fun scrollToIndex(index: Int, smooth: Boolean = true) {
        setScrollOffset(index.toFloat(), smooth)
    }

    fun snapToNearest() {
        queueEvent { renderer.snapToNearest() }
        requestRender()
    }

    fun getScrollOffset(): Float = renderer.scrollOffset
    fun getCenteredIndex(): Int = renderer.centeredIndex()

    // Listener pass-through
    fun addScrollListener(listener: ScrollListener) {
        renderer.addScrollListener(listener)
    }

    fun removeScrollListener(listener: ScrollListener) {
        renderer.removeScrollListener(listener)
    }

    fun clearScrollListeners() {
        renderer.clearScrollListeners()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        queueEvent { renderer.release() }
        stopAnimating()
        unregisterSharedPreferenceChangeListener()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (scroller.isFinished) renderer.snapToNearest()
            queueEvent { renderer.endVerticalDrag() }
        }
        return handled || super.onTouchEvent(event)
    }

    // ----- Animation loop via Choreographer -----
    private fun ensureAnimating() {
        if (!animating) {
            animating = true
            choreographer.postFrameCallback(this)
        }
    }

    private fun stopAnimating() {
        if (animating) {
            animating = false
            choreographer.removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!scroller.isFinished) {
            scroller.computeScrollOffset()
            val curr = scroller.currX // we use X channel generically
            val dx = (curr - lastFlingCoord) / 1000f
            lastFlingCoord = curr
            renderer.scrollBy(dx)
            requestRender()
            choreographer.postFrameCallback(this)
        } else {
            renderer.snapToNearest()
            requestRender()
            // Keep one more frame for settling, then stop
            stopAnimating()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            CarouselPreferences.CAMERA_EYE_Y -> {
                queueEvent { renderer.updateCamera() }
            }
        }
    }

    override fun setAlpha(alpha: Float) {
        Log.i("CoverFlow", "Setting global alpha to $alpha")
        renderer.setGlobalAlpha(alpha)
    }
}