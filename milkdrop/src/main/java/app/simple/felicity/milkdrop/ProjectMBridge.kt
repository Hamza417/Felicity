package app.simple.felicity.milkdrop

/**
 * JNI bindings for the native projectM 4.x milkdrop visualizer.
 *
 * Each public method maps 1:1 to a corresponding `extern "C"` function in
 * [milkdrop-bridge.cpp].  An opaque [nativeHandle] (actually a C pointer cast to
 * [Long]) is kept as instance state; all native calls pass it as their first
 * argument so the bridge can dispatch to the correct [projectm_handle].
 *
 * Typical usage from a [android.opengl.GLSurfaceView.Renderer]:
 * ```kotlin
 * // onSurfaceCreated
 * bridge.create(width, height)
 *
 * // onSurfaceChanged
 * bridge.surfaceChanged(width, height)
 *
 * // onDrawFrame — audio thread feeds PCM concurrently via addPcmData
 * bridge.renderFrame()
 *
 * // onSurfaceDestroyed / lifecycle cleanup
 * bridge.destroy()
 * ```
 *
 * @author Hamza417
 */
class ProjectMBridge {

    /**
     * Opaque pointer to the native [projectm_handle]; 0 when no instance exists.
     * Exposed internally for testing and subclasses.
     */
    internal var nativeHandle: Long = 0L
        private set

    /**
     * Returns `true` if a native projectM instance is alive.
     */
    val isCreated: Boolean
        get() = nativeHandle != 0L

    /**
     * Creates the native projectM instance for the given initial viewport size.
     *
     * An OpenGL ES context must be current on the calling thread.  This method
     * is idempotent — calling it when [isCreated] is `true` is a no-op.
     *
     * @param width  Initial surface width in pixels.
     * @param height Initial surface height in pixels.
     */
    fun create(width: Int, height: Int) {
        if (isCreated) return
        nativeHandle = nativeCreate(width, height)
    }

    /**
     * Notifies the native renderer that the surface dimensions changed.
     *
     * Safe to call from the GL thread at any time after [create].
     *
     * @param width  New surface width in pixels.
     * @param height New surface height in pixels.
     */
    fun surfaceChanged(width: Int, height: Int) {
        if (!isCreated) return
        nativeSurfaceChanged(nativeHandle, width, height)
    }

    /**
     * Feeds PCM float samples into the projectM audio pipeline.
     *
     * This function is thread-safe and can be called from the audio thread while
     * [renderFrame] runs concurrently on the GL thread.  Samples must be in the
     * range `[-1.0, 1.0]`.
     *
     * @param samples  PCM samples.  For mono: [count] elements.
     *                 For stereo: [count * 2] interleaved elements (L, R, L, R, …).
     * @param count    Number of frames (mono samples, or stereo pairs).
     * @param isStereo Pass `true` for interleaved stereo data, `false` for mono.
     *                 Defaults to `false` because the [VisualizerProcessor] tap
     *                 provides a mono downmix.
     */
    fun addPcmData(samples: FloatArray, count: Int, isStereo: Boolean = false) {
        if (!isCreated) return
        nativeAddPcmData(nativeHandle, samples, count, isStereo)
    }

    /**
     * Renders the next visualizer frame into the currently bound OpenGL ES framebuffer.
     *
     * Must be called from the GL thread with the OpenGL ES context current.
     */
    fun renderFrame() {
        if (!isCreated) return
        nativeRenderFrame(nativeHandle)
    }

    /**
     * Destroys the native projectM instance and releases all associated resources.
     *
     * After this call [isCreated] returns `false`.  A new instance can be created
     * by calling [create] again.
     */
    fun destroy() {
        if (!isCreated) return
        nativeDestroy(nativeHandle)
        nativeHandle = 0L
    }

    /**
     * Loads a Milkdrop preset from its raw text content.
     *
     * Must be called on the GL thread (via [android.opengl.GLSurfaceView.queueEvent]) while
     * the OpenGL ES context is current.
     *
     * @param content Full text of the `.milk` preset file.
     * @param smooth  When `true`, projectM cross-fades into the new preset.
     *                Defaults to `true` for a visually smooth transition.
     */
    fun loadPresetData(content: String, smooth: Boolean = true) {
        if (!isCreated) return
        nativeLoadPresetData(nativeHandle, content, smooth)
    }

    // Native declarations — implemented in milkdrop-bridge.cpp

    private external fun nativeCreate(width: Int, height: Int): Long
    private external fun nativeSurfaceChanged(nativePtr: Long, width: Int, height: Int)
    private external fun nativeAddPcmData(nativePtr: Long, samples: FloatArray, count: Int, isStereo: Boolean)
    private external fun nativeRenderFrame(nativePtr: Long)
    private external fun nativeLoadPresetData(nativePtr: Long, data: String, smooth: Boolean)
    private external fun nativeDestroy(nativePtr: Long)

    companion object {
        init {
            System.loadLibrary("felicity_milkdropd")
        }
    }
}

