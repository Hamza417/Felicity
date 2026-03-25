package app.simple.felicity.ui.panels

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentMilkdropBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.dialogs.player.MilkdropPresets.Companion.showMilkdropPresets
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.MilkdropPreferences
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Audio
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
        requireHiddenMiniPlayer()
        updateState()

        binding.seekbar.setLabelBackgroundEnabled(false)
        applyControlsOverlayColors()

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

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
        }

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.toLong().div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.toLong().div(1000))
        }

        binding.search.setOnClickListener {
            openFragment(Search.newInstance(), Search.TAG)
        }

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    MediaManager.seekTo(progress.toLong())
                }
            }
        })
    }

    /**
     * Tints every interactive element in the controls overlay panel to 70 % opaque white
     * so that the controls feel soft and non-distracting against the dark Milkdrop surface.
     *
     * This must be called after view inflation because the underlying custom views
     * ([ThemeImageButton][app.simple.felicity.decorations.theme.ThemeImageButton] and
     * [TypeFaceTextView]) override colors inside their own `init` blocks; programmatic
     * assignment after inflation therefore wins over any XML color attribute.
     */
    private fun applyControlsOverlayColors() {
        val semiWhite = ColorStateList.valueOf(Color.argb(179, 255, 255, 255))
        binding.next.imageTintList = semiWhite
        binding.previous.imageTintList = semiWhite
        binding.search.imageTintList = semiWhite
        binding.play.iconColor = Color.argb(179, 255, 255, 255)
        binding.name.setTextColor(semiWhite)
        binding.artist.setTextColor(semiWhite)
        binding.controlsContainer.setBackgroundColor(Color.TRANSPARENT)
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

    private fun updateState() {
        val audio = MediaManager.getCurrentSong() ?: return
        binding.name.text = audio.title
        binding.artist.text = audio.artist
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updatePlayButtonState(MediaManager.isPlaying())
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            binding.play.playing()
        } else {
            binding.play.paused()
        }
    }

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.seekbar.setProgress(seek.toFloat(), fromUser = false, animate = true)
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)

        val forward = MediaManager.lastNavigationDirection
        binding.name.setTextWithEffect(audio.title ?: getString(R.string.unknown), forward)
        binding.artist.setTextWithEffect(audio.artist ?: getString(R.string.unknown), forward, 50L)
        binding.seekbar.setMaxWithReset(audio.duration.toFloat())

        // Always refresh the seek position (covers predictive-back resume and actual changes).
        binding.seekbar.setProgress(MediaManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        when (state) {
            MediaConstants.PLAYBACK_PLAYING -> {
                updatePlayButtonState(true)
            }
            MediaConstants.PLAYBACK_PAUSED -> {
                updatePlayButtonState(false)
            }
        }
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

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        /** Back-stack tag used when adding this fragment to the back stack. */
        const val TAG = "Milkdrop"

        /** Creates a new instance with no arguments. */
        fun newInstance(): Milkdrop = Milkdrop()
    }
}
