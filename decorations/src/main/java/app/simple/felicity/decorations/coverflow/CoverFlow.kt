package app.simple.felicity.decorations.coverflow

import android.content.Context
import android.net.Uri
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.OverScroller
import app.simple.felicity.decorations.coverflow.CoverFlowRenderer.ScrollListener

class CoverFlow @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), Choreographer.FrameCallback {

    private val renderer: CoverFlowRenderer
    private val gestureDetector: GestureDetector
    private val scroller: OverScroller
    private val choreographer = Choreographer.getInstance()

    private var animating = false
    private var lastFlingX = 0

    init {
        setEGLContextClientVersion(2)
        renderer = CoverFlowRenderer(this, context.applicationContext)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scroller = OverScroller(context)

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.abortAnimation()
                lastFlingX = (renderer.scrollOffset * 1000).toInt()
                ensureAnimating()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // Fix: use distanceX for horizontal scrolling with better sensitivity
                renderer.scrollBy(distanceX / (width * 0.3f))
                requestRender()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // Fix: use proper horizontal velocity scaling
                val velocityItemsPerSec = velocityX / (width * 0.5f)
                val start = (renderer.scrollOffset * 1000).toInt()
                val vel = (velocityItemsPerSec * 1000).toInt()
                scroller.fling(
                        start, 0,
                        -vel, 0,
                        Int.MIN_VALUE / 4, Int.MAX_VALUE / 4,
                        0, 0
                )
                lastFlingX = start
                ensureAnimating()
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                renderer.snapToNearest()
                requestRender()
                return true
            }
        })
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
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (scroller.isFinished) renderer.snapToNearest()
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
            val curr = scroller.currX
            val dx = (curr - lastFlingX) / 1000f
            lastFlingX = curr
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
}