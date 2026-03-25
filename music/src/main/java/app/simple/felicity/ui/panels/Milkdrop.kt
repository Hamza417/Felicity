package app.simple.felicity.ui.panels

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.databinding.FragmentMilkdropBinding
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment

/**
 * Full-screen Milkdrop visualizer fragment.
 *
 * Hosts a [app.simple.felicity.milkdrop.views.MilkdropSurfaceView] that renders
 * projectM 4.x presets in real time.  Audio data is delivered to projectM via the
 * [VisualizerProcessor][app.simple.felicity.engine.processors.VisualizerProcessor]
 * PCM-window tap, which the surface view manages automatically on attach/detach.
 *
 * The fragment re-registers the PCM tap in [onViewCreated] to cover the edge case
 * where the player service started after [onAttachedToWindow] fired.  The tap is
 * also explicitly cleared in [onDestroyView] so the audio thread holds no stale
 * reference to the renderer after the view hierarchy is torn down.
 *
 * [GLSurfaceView.onResume] and [GLSurfaceView.onPause] are forwarded from the
 * fragment lifecycle so that the EGL rendering thread pauses correctly when the app
 * is backgrounded.
 *
 * @author Hamza417
 */
class Milkdrop : MediaFragment() {

    private lateinit var binding: FragmentMilkdropBinding

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentMilkdropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Hide the mini player — this is a full-screen visualizer.
        requireHiddenMiniPlayer()

        // Re-register the PCM tap in case the player service started after the
        // surface view's onAttachedToWindow fired (which would have left the tap null).
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume the GL render thread when the fragment comes back to the foreground.
        binding.milkdropSurface.onResume()
        // Re-register in case the processor was replaced while paused (service restart).
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }
    }

    override fun onPause() {
        // Pause the GL render thread before the fragment goes to the background.
        // Must be called before super.onPause() so the surface is still valid.
        binding.milkdropSurface.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        // Sever the PCM tap before the view hierarchy is torn down so the audio
        // thread cannot write into a destroyed renderer.
        binding.milkdropSurface.disconnectProcessor()
        super.onDestroyView()
    }

    /** This fragment occupies the full screen; the mini player must stay hidden. */
    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        /** Back-stack tag used when adding this fragment to the back stack. */
        const val TAG = "Milkdrop"

        /** Creates a new instance with no arguments. */
        fun newInstance(): Milkdrop = Milkdrop()
    }
}

