package app.simple.felicity.milkdrop.renderer

import android.opengl.GLSurfaceView
import android.util.Log
import app.simple.felicity.engine.processors.VisualizerProcessor
import app.simple.felicity.milkdrop.bridges.ProjectMBridge
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

    /**
     * Raw text content of the most recently loaded `.milk` preset, or `null` if no preset
     * has been loaded yet in this renderer instance.
     *
     * This is written and read exclusively on the GL thread, so no synchronization is needed.
     * It is used to restore the active preset immediately after [ProjectMBridge.create] so
     * that EGL context loss events (e.g., those triggered by Android's hardware-layer
     * compositing path when a parent view's alpha drops below 1.0 during a fragment
     * transition) do not leave projectM displaying its built-in default preset.
     */
    private var lastPresetContent: String? = null

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
     * Every call to this method signals that all OpenGL objects (textures, programs,
     * framebuffers) that were allocated in any previous context are now invalid. The
     * bridge is therefore unconditionally destroyed and recreated here regardless of
     * whether it was previously initialized.
     *
     * This handles both the initial creation and any EGL context-loss recovery scenario,
     * including the one triggered by Android's hardware-layer compositing path when a
     * parent view has alpha less than 1.0 applied during a fragment transition.
     *
     * @param gl     Legacy GL 1.x interface — unused; this renderer targets ES 3.
     * @param config EGL configuration for the surface.
     */
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated — size=${surfaceWidth}x${surfaceHeight}")
        // Destroy any existing bridge before recreating. The native destroy call may
        // attempt to release GL objects; since they belonged to the now-dead context
        // the GL driver will silently ignore deletions of unknown object IDs, so this
        // is safe even when called with a brand-new context current.
        bridge.destroy()
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            bridge.create(surfaceWidth, surfaceHeight)
            // Immediately restore the last active preset so that EGL context loss events
            // (e.g., triggered by hardware-layer compositing during fragment transitions)
            // do not leave projectM displaying its built-in default preset.
            lastPresetContent?.let { bridge.loadPresetData(it, smooth = false) }
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
     * Loads a Milkdrop preset from raw text content.
     *
     * Must be called on the GL thread (e.g. via [android.opengl.GLSurfaceView.queueEvent])
     * while the EGL context is current.
     *
     * The content is also cached in [lastPresetContent] so it can be restored automatically
     * if the EGL context is lost and recreated (see [onSurfaceCreated]).
     *
     * @param content Full text content of the `.milk` preset file.
     * @param smooth  When `true`, projectM cross-fades into the new preset.
     */
    fun loadPreset(content: String, smooth: Boolean = true) {
        lastPresetContent = content
        bridge.loadPresetData(content, smooth)
    }

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

