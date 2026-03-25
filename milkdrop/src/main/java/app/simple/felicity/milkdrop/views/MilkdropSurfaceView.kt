package app.simple.felicity.milkdrop.views

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.milkdrop.renderer.MilkdropGLRenderer

/**
 * A [GLSurfaceView] that renders the projectM 4.x milkdrop visualizer.
 *
 * On attachment to a window the view registers [MilkdropGLRenderer] as the
 * [VisualizerProcessor][app.simple.felicity.engine.processors.VisualizerProcessor]
 * PCM-window callback so the audio thread can feed raw mono PCM directly into
 * projectM without any intermediate queuing or allocation.
 *
 * The EGL context is configured for OpenGL ES 3.0 with an RGBA-8888 color buffer
 * and a 16-bit depth buffer — the minimum requirements for projectM 4.x shaders.
 *
 * Rendering runs in [RENDERMODE_CONTINUOUSLY] at the display refresh rate.  The
 * host fragment is responsible for calling [onResume] and [onPause] at the
 * appropriate lifecycle events to pause rendering when the app is backgrounded.
 *
 * @author Hamza417
 */
class MilkdropSurfaceView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    /** The renderer instance; exposed so the host fragment can register / unregister the PCM tap. */
    val renderer: MilkdropGLRenderer = MilkdropGLRenderer()

    init {
        // OpenGL ES 3.0 — required by projectM 4.x GLSL shaders.
        setEGLContextClientVersion(3)

        // RGBA-8888 color buffer + 16-bit depth buffer. No stencil needed.
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        setRenderer(renderer)

        // Render at the display refresh rate regardless of whether new audio data
        // has arrived; projectM interpolates smoothly between audio windows.
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // ── Window attachment / detachment ────────────────────────────────────────

    /**
     * Registers the renderer as the PCM-window callback on the live
     * [VisualizerProcessor][app.simple.felicity.engine.processors.VisualizerProcessor]
     * so that audio data flows immediately when the view becomes visible.
     *
     * If the player service has not started yet and [VisualizerManager.processor] is
     * null, the host fragment should call [connectProcessor] once the service is
     * available.
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        VisualizerManager.processor?.setPcmWindowCallback(renderer)
    }

    /**
     * Unregisters the PCM-window callback and queues a bridge-destroy event on the
     * GL thread before [super.onDetachedFromWindow] stops the render thread.
     *
     * The [GLSurfaceView] implementation of [onDetachedFromWindow] calls
     * [requestExitAndWait][android.opengl.GLSurfaceView] which drains the GL event
     * queue, so the destroy event is guaranteed to execute before the EGL context
     * is torn down.
     */
    override fun onDetachedFromWindow() {
        VisualizerManager.processor?.setPcmWindowCallback(null)
        queueEvent { renderer.destroy() }
        super.onDetachedFromWindow()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Loads a Milkdrop preset from raw text content.
     *
     * The call is automatically marshalled to the GL thread via [queueEvent] so it is
     * safe to call from any thread (e.g. the main thread inside a [viewLifecycleOwner]
     * coroutine).
     *
     * @param content Full text content of the `.milk` preset file.
     * @param smooth  When `true`, projectM cross-fades into the new preset.
     */
    fun loadPreset(content: String, smooth: Boolean = true) {
        queueEvent { renderer.loadPreset(content, smooth) }
    }

    /**
     * Explicitly registers the renderer's PCM tap on [processor].
     *
     * Call this from the host fragment when [VisualizerManager.processor] was null
     * at the time [onAttachedToWindow] fired (i.e., the service started after the
     * fragment was shown).
     *
     * @param processor The live [VisualizerProcessor] provided by the player service.
     */
    fun connectProcessor(processor: app.simple.felicity.engine.processors.VisualizerProcessor) {
        processor.setPcmWindowCallback(renderer)
    }

    /**
     * Explicitly unregisters the renderer's PCM tap.
     *
     * Call this from the host fragment's [onDestroyView] to ensure the audio thread
     * does not hold a reference to the renderer after the view is gone.
     */
    fun disconnectProcessor() {
        VisualizerManager.processor?.setPcmWindowCallback(null)
    }
}

