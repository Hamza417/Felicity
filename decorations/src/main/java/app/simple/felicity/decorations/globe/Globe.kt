package app.simple.felicity.decorations.globe

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import app.simple.felicity.decorations.artflow.ArtFlowDataProvider
import kotlin.math.sqrt

/**
 * A [android.opengl.GLSurfaceView] that renders all album arts as textured quads distributed on a 3-D sphere.
 *
 * Supported gestures:
 *  - Single-finger drag: rotates the sphere itself around its center (trackball style).
 *    The rotation axis is derived from the drag direction so the sphere never drifts off-screen.
 *  - Pinch: scales the sphere in world space to zoom in/out.
 *    The camera and frustum remain completely fixed.
 *  - Single tap: fires [OnAlbumTapListener] for the album art under the finger.
 *
 * @author Hamza417
 */
class Globe @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: GlobeRenderer
    private val gestureDetector: GestureDetector
    private val scaleDetector: ScaleGestureDetector

    /** Stores the previous single-pointer position for computing drag deltas. */
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    /**
     * Degrees of sphere rotation per pixel of finger travel.
     * Lower values feel slower/heavier; higher values feel more responsive.
     */
    private val rotationSensitivity = 0.35f

    /**
     * Accumulated zoom scale applied to the sphere object.
     * Persists across gesture sessions so successive pinches compose correctly.
     */
    private var currentScale = 1.0f

    /** Listener called when the user taps an album art. */
    private var albumTapListener: OnAlbumTapListener? = null

    /**
     * Callback interface for album-art tap events.
     */
    interface OnAlbumTapListener {
        /**
         * Invoked when the user taps an album art on the globe.
         *
         * @param index  Zero-based index into the data provider.
         * @param itemId Opaque identifier from [app.simple.felicity.decorations.artflow.ArtFlowDataProvider.getItemId].
         */
        fun onAlbumTapped(index: Int, itemId: Any?)
    }

    /** Attach a listener to receive tap notifications. */
    fun setOnAlbumTapListener(listener: OnAlbumTapListener?) {
        albumTapListener = listener
    }

    /** Supply the data source. Triggers an immediate GL-thread reload. */
    fun setDataProvider(provider: ArtFlowDataProvider) {
        queueEvent { renderer.setDataProvider(provider) }
        requestRender()
    }

    init {
        setEGLContextClientVersion(2)
        renderer = GlobeRenderer(this, context.applicationContext)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        // Scale the sphere object itself — camera and frustum never change.
                        currentScale = (currentScale * detector.scaleFactor).coerceIn(0.3f, 2.8f)
                        renderer.setScale(currentScale)
                        requestRender()
                        return true
                    }
                })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hit = renderer.pickAtScreen(e.x, e.y)
                if (hit != null) {
                    albumTapListener?.onAlbumTapped(hit, renderer.getItemIdAt(hit))
                }
                return true
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    applyDragRotation(dx, dy)
                    requestRender()
                } else {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
        }
        return true
    }

    /**
     * Converts a 2-D screen drag into a single-axis 3-D rotation applied to the sphere.
     *
     * The rotation axis is always perpendicular to the drag direction in screen space:
     * horizontal drag (dx) maps to the world Y axis, vertical drag (dy) to world X.
     * Using one combined axis instead of two sequential rotations avoids gimbal lock
     * and ensures the sphere always rotates around its own center without drifting.
     *
     * @param dx Horizontal drag distance in pixels (positive = right).
     * @param dy Vertical drag distance in pixels (positive = down).
     */
    private fun applyDragRotation(dx: Float, dy: Float) {
        val magnitude = sqrt(dx * dx + dy * dy)
        if (magnitude < 0.5f) return

        val angle = magnitude * rotationSensitivity

        // Normalize the drag vector to get the rotation axis.
        // A rightward swipe (dx > 0) rotates around +Y (top of sphere comes toward viewer).
        // A downward swipe (dy > 0) rotates around +X (front of sphere tilts down).
        val axisX = dy / magnitude
        val axisY = dx / magnitude

        val delta = FloatArray(16)
        Matrix.setIdentityM(delta, 0)
        Matrix.rotateM(delta, 0, angle, axisX, axisY, 0f)
        queueEvent { renderer.applyRotationDelta(delta) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        queueEvent { renderer.release() }
    }
}