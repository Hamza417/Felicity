package app.simple.felicity.milkdrop.renderer

import android.opengl.GLSurfaceView
import android.util.Log
import app.simple.felicity.engine.processors.VisualizerProcessor
import app.simple.felicity.milkdrop.ProjectMBridge
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 3 renderer that drives the projectM 4.x visualizer.
 *
 * This class serves a dual role:
 *  - As a [GLSurfaceView.Renderer] it manages the projectM instance lifecycle
 *    (create on first surface, resize on configuration changes, render each frame).
 *  - As a [VisualizerProcessor.PcmWindowCallback] it receives mono PCM windows
 *    from the audio thread and feeds them directly to projectM's internal audio
 *    queue, which is protected by projectM's own mutex.
 *
 * Threading model:
 *  - Audio thread  → [onPcmWindow] → [ProjectMBridge.addPcmData] (JNI, thread-safe)
 *  - GL thread     → [onDrawFrame]  → [ProjectMBridge.renderFrame] (always current context)
 *
 * [ProjectMBridge.create] must be called with an active OpenGL ES context, so it is
 * deferred to [onSurfaceCreated] / [onSurfaceChanged] rather than the constructor.
 *
 * @author Hamza417
 */
class MilkdropGLRenderer : GLSurfaceView.Renderer, VisualizerProcessor.PcmWindowCallback {

    private val bridge = ProjectMBridge()

    /**
     * Width of the current EGL surface in pixels.
     * Cached so [onSurfaceCreated] can call [ProjectMBridge.create] with the correct size
     * even when [onSurfaceChanged] fires after [onSurfaceCreated] on the same frame.
     */
    private var surfaceWidth = 0

    /**
     * Height of the current EGL surface in pixels.
     * See [surfaceWidth] for the rationale.
     */
    private var surfaceHeight = 0

    // ── VisualizerProcessor.PcmWindowCallback ─────────────────────────────────

    /**
     * Receives a raw mono PCM window from the audio thread and feeds it to projectM.
     *
     * This method is called on the audio thread approximately every
     * `[VisualizerProcessor.FFT_SIZE] / sampleRate` seconds (≈ 93 ms at 44 100 Hz).
     * [ProjectMBridge.addPcmData] copies the samples into projectM's internal ring
     * buffer synchronously before returning, so [samples] may be reused by the caller
     * immediately after this call.
     *
     * @param samples Raw mono PCM data (one FFT window); do not retain past this call.
     * @param count   Number of valid samples — always [VisualizerProcessor.FFT_SIZE].
     */
    override fun onPcmWindow(samples: FloatArray, count: Int) {
        bridge.addPcmData(samples, count, isStereo = false)
    }

    // ── GLSurfaceView.Renderer ────────────────────────────────────────────────

    /**
     * Called by the GL thread when a fresh EGL context is created.
     *
     * The initial size may be (0, 0) here; [onSurfaceChanged] fires immediately after
     * with the real dimensions. If size is already known (e.g., after a context loss
     * recovery), the bridge is created here with the cached size.
     *
     * @param gl     Legacy GL 1.x interface — unused; this renderer targets ES 3.
     * @param config EGL configuration for the surface.
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated — size=${surfaceWidth}x${surfaceHeight}")
        if (surfaceWidth > 0 && surfaceHeight > 0 && !bridge.isCreated) {
            bridge.create(surfaceWidth, surfaceHeight)
        }
    }

    /**
     * Called by the GL thread when the surface size changes (including on initial creation).
     *
     * If the bridge has not yet been created, it is created here with the definitive
     * surface dimensions.  Otherwise a resize is forwarded to the existing instance.
     *
     * @param gl     Legacy GL 1.x interface — unused.
     * @param width  New surface width in pixels.
     * @param height New surface height in pixels.
     */
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        Log.i(TAG, "onSurfaceChanged — ${width}x${height}")

        if (!bridge.isCreated) {
            bridge.create(width, height)
        } else {
            bridge.surfaceChanged(width, height)
        }
    }

    /**
     * Called by the GL thread once per frame.
     *
     * Delegates directly to [ProjectMBridge.renderFrame] which calls
     * [projectm_opengl_render_frame] on the currently bound OpenGL ES context.
     *
     * @param gl Legacy GL 1.x interface — unused.
     */
    override fun onDrawFrame(gl: GL10?) {
        bridge.renderFrame()
    }

    // ── Cleanup ────────────────────────────────────────────────────────────────

    /**
     * Destroys the native projectM instance.
     *
     * Must be called on the GL thread (via [GLSurfaceView.queueEvent]) so that the
     * OpenGL ES context is still current when projectM releases its GPU resources.
     */
    fun destroy() {
        Log.i(TAG, "destroy")
        bridge.destroy()
    }

    private companion object {
        private const val TAG = "MilkdropGLRenderer"
    }
}

