package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.databinding.FragmentMilkdropBinding
import app.simple.felicity.dialogs.player.MilkdropPresets.Companion.showMilkdropPresets
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.MilkdropPreferences
import app.simple.felicity.viewmodels.panels.MilkdropViewModel
import kotlinx.coroutines.launch

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
 * Preset selection is persisted in [MilkdropPreferences] and reloaded via
 * [MilkdropViewModel].  Whenever [MilkdropPreferences.LAST_PRESET] changes (from
 * the presets dialog), [onSharedPreferenceChanged] triggers a ViewModel reload which
 * re-emits the new preset content and calls [loadCurrentPreset].
 *
 * [GLSurfaceView.onResume] and [GLSurfaceView.onPause] are forwarded from the
 * fragment lifecycle so that the EGL rendering thread pauses correctly when the app
 * is backgrounded.
 *
 * @author Hamza417
 */
class Milkdrop : MediaFragment() {

    private lateinit var binding: FragmentMilkdropBinding

    private val viewModel: MilkdropViewModel by viewModels()

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

        requireLightBarIcons()
        requireTransparentMiniPlayer()

        // Re-register the PCM tap in case the player service started after the
        // surface view's onAttachedToWindow fired (which would have left the tap null).
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }

        // Open the presets bottom-sheet on the Presets button.
        binding.presets.setOnClickListener {
            childFragmentManager.showMilkdropPresets()
        }

        // Observe preset content emitted by the ViewModel and load it into projectM.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presetContent.collect { content ->
                    content?.let { loadCurrentPreset(it) }
                }
            }
        }
    }

    /**
     * Passes the preset text to the surface view, which marshals the call to the GL thread
     * via [android.opengl.GLSurfaceView.queueEvent].
     *
     * @param content Full text content of the `.milk` preset file.
     */
    private fun loadCurrentPreset(content: String) {
        binding.milkdropSurface.loadPreset(content, smooth = true)
    }

    override fun onResume() {
        super.onResume()
        binding.milkdropSurface.onResume()
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }
    }

    override fun onPause() {
        binding.milkdropSurface.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding.milkdropSurface.disconnectProcessor()
        super.onDestroyView()
    }

    /**
     * When the user picks a preset in [app.simple.felicity.dialogs.player.MilkdropPresets],
     * the dialog saves [MilkdropPreferences.LAST_PRESET] which triggers this callback.
     * Asking the ViewModel to reload causes it to read the new file from assets and
     * re-emit [MilkdropViewModel.presetContent], which the collector above will forward
     * to projectM.
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            MilkdropPreferences.LAST_PRESET -> viewModel.reloadFromPreferences()
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    companion object {
        /** Back-stack tag used when adding this fragment to the back stack. */
        const val TAG = "Milkdrop"

        /** Creates a new instance with no arguments. */
        fun newInstance(): Milkdrop = Milkdrop()
    }
}
