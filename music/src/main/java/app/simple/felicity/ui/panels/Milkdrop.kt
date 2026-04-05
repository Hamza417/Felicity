package app.simple.felicity.ui.panels

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.lists.AdapterMilkdropPager
import app.simple.felicity.databinding.FragmentMilkdropBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.decorations.utils.TextViewUtils.setTextWithEffect
import app.simple.felicity.dialogs.player.MilkdropPresets.Companion.showMilkdropPresets
import app.simple.felicity.engine.managers.MediaPlaybackManager
import app.simple.felicity.engine.managers.VisualizerManager
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.AppearancePreferences.getCornerRadius
import app.simple.felicity.repository.constants.MediaConstants
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.utils.AudioUtils.getArtists
import app.simple.felicity.ui.panels.Milkdrop.Companion.OVERLAY_VISIBLE_MS
import app.simple.felicity.viewmodels.panels.MilkdropViewModel
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch

/**
 * Full-screen Milkdrop visualizer fragment.
 *
 * Hosts a [app.simple.felicity.milkdrop.views.MilkdropSurfaceView] that renders
 * projectM 4.x presets in real time.  Audio data is delivered via the
 * [VisualizerProcessor][app.simple.felicity.engine.processors.VisualizerProcessor]
 * PCM-window tap managed by the surface view on attach/detach.
 *
 * A semi-transparent overlay at the top of the screen contains a [ViewPager2] that
 * lists every bundled preset.  Swiping left or right scrolls through presets and
 * immediately loads the newly selected one into projectM.  The overlay fades out
 * automatically after [OVERLAY_VISIBLE_MS] milliseconds of inactivity and reappears
 * whenever the screen is tapped.
 *
 * `GLSurfaceView.onResume` and `GLSurfaceView.onPause` are forwarded from the
 * fragment lifecycle so that the EGL rendering thread pauses correctly when the app
 * is backgrounded.
 *
 * @author Hamza417
 */
class Milkdrop : MediaFragment() {

    private lateinit var binding: FragmentMilkdropBinding

    private val viewModel: MilkdropViewModel by viewModels()

    private var pagerAdapter: AdapterMilkdropPager? = null

    private val fadeHandler = Handler(Looper.getMainLooper())

    private val fadeOutRunnable = Runnable {
        binding.presetPagerContainer
            .animate()
            .alpha(0.25f)
            .setDuration(FADE_DURATION_MS)
            .start()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentMilkdropBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireLightBarIcons()
        requireHiddenMiniPlayer()
        setPresetPagerBackground()

        // Re-register the PCM tap in case the player service started after the
        // surface view's onAttachedToWindow fired (which would have left the tap null).
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }

        setupPresetPager()
        observeViewModel()

        // Show overlay on any tap on the surface view (GLSurfaceView does not consume touches).
        val surfaceTouchListener = { v: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> showOverlay()
                MotionEvent.ACTION_UP -> v.performClick()
            }
            false
        }
        binding.milkdropSurface.setOnTouchListener(surfaceTouchListener)

        // Catch any unhandled taps on the root so the overlay reappears regardless of
        // which part of the screen the user touches.
        binding.root.setOnClickListener { showOverlay() }

        // Toggle the automatic preset shuffle and reset the fade timer so the user
        // can immediately see the new button state.
        binding.shufflePreset.setOnClickListener {
            viewModel.toggleShuffle()
            showOverlay()
        }

        binding.seekbar.setLeftLabelProvider { progress, _, _ ->
            DateUtils.formatElapsedTime(progress.toLong().div(1000))
        }

        binding.seekbar.setRightLabelProvider { _, _, max ->
            DateUtils.formatElapsedTime(max.toLong().div(1000))
        }

        binding.seekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    MediaPlaybackManager.seekTo(progress.toLong())
                }
            }
        })

        binding.next.setOnClickListener {
            MediaPlaybackManager.next()
        }

        binding.previous.setOnClickListener {
            MediaPlaybackManager.previous()
        }

        binding.play.setOnClickListener {
            MediaPlaybackManager.flipState()
        }

        // Schedule the first auto-hide so the overlay does not linger on cold start.
        scheduleOverlayFadeOut()
    }

    /**
     * Configures the [ViewPager2] adapter and registers the page-change callback that
     * triggers a preset load whenever the user swipes to a new page.
     */
    private fun setupPresetPager() {
        pagerAdapter = AdapterMilkdropPager {
            childFragmentManager.showMilkdropPresets()
        }

        binding.presetPager.adapter = pagerAdapter

        binding.presetPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.loadPresetAtIndex(position)
            }
        })
    }

    /**
     * Collects all ViewModel flows and routes updates to the UI.
     *
     * - [MilkdropViewModel.presets]          — populates the pager adapter and restores the
     *   saved scroll position on first emission.
     * - [MilkdropViewModel.currentIndex]     — scrolls the pager when shuffle picks a new preset.
     * - [MilkdropViewModel.presetContent]    — pushes new preset text into projectM.
     * - [MilkdropViewModel.isShuffleEnabled] — updates the shuffle button visual state.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { list ->
                    pagerAdapter?.submitList(list)
                    // Restore the previously saved position once the list is ready.
                    if (list.isNotEmpty()) {
                        val index = viewModel.currentIndex.value.coerceIn(0, list.lastIndex)
                        binding.presetPager.setCurrentItem(index, false)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentIndex.collect { index ->
                    val itemCount = pagerAdapter?.itemCount ?: 0
                    if (itemCount > 0 && index != binding.presetPager.currentItem) {
                        // Smooth-scroll so the shuffle transition is visible to the user.
                        binding.presetPager.setCurrentItem(index, false)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presetContent.collect { content ->
                    content?.let { loadCurrentPreset(it) }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isShuffleEnabled.collect { enabled ->
                    // Full opacity when shuffle is on; dimmed when off.
                    binding.shufflePreset.alpha = if (enabled) 1.0f else 0.35f
                }
            }
        }
    }

    /**
     * Makes the preset pager overlay fully visible and resets the auto-hide countdown.
     *
     * Safe to call from any event handler (touch, page change, etc.).
     */
    private fun showOverlay() {
        fadeHandler.removeCallbacks(fadeOutRunnable)
        binding.presetPagerContainer
            .animate()
            .alpha(1f)
            .setDuration(FADE_DURATION_MS)
            .start()
        scheduleOverlayFadeOut()
    }

    /**
     * Posts [fadeOutRunnable] to run after [OVERLAY_VISIBLE_MS] milliseconds.
     *
     * Any previously pending post is removed by [showOverlay] before this is called,
     * so the timer always restarts from zero on each interaction.
     */
    private fun scheduleOverlayFadeOut() {
        fadeHandler.postDelayed(fadeOutRunnable, OVERLAY_VISIBLE_MS)
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

    private fun setPresetPagerBackground() {
        val shapeAppearanceModel = ShapeAppearanceModel()
            .toBuilder()
            .setAllCorners(CornerFamily.ROUNDED, getCornerRadius())
            .build()

        val materialShapeDrawable = MaterialShapeDrawable(shapeAppearanceModel)

        materialShapeDrawable.setStroke(0.5F, Color.DKGRAY)

        binding.presetPagerContainer.background = materialShapeDrawable
    }

    private fun updateState() {
        val audio = MediaPlaybackManager.getCurrentSong() ?: return
        binding.name.text = audio.title
        binding.artist.text = audio.getArtists()
        binding.seekbar.setMax(audio.duration.toFloat())
        binding.seekbar.setProgress(MediaPlaybackManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
        updatePlayButtonState(MediaPlaybackManager.isPlaying())
    }

    private fun updatePlayButtonState(isPlaying: Boolean) {
        if (isPlaying) {
            binding.play.playing()
        } else {
            binding.play.paused()
        }
    }

    override fun onAudio(audio: Audio) {
        super.onAudio(audio)
        val forward = MediaPlaybackManager.lastNavigationDirection
        binding.name.setTextWithEffect(audio.title ?: getString(R.string.unknown), forward)
        binding.artist.setTextWithEffect(audio.getArtists(), forward, 50L)
        binding.seekbar.setMaxWithReset(audio.duration.toFloat())

        // Always refresh the seek position (covers predictive-back resume and actual changes).
        binding.seekbar.setProgress(MediaPlaybackManager.getSeekPosition().toFloat(), fromUser = false, animate = true)
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

    override fun onSeekChanged(seek: Long) {
        super.onSeekChanged(seek)
        binding.seekbar.setProgress(seek.toFloat(), fromUser = false, animate = true)
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

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
        fadeHandler.removeCallbacksAndMessages(null)
        pagerAdapter = null
        binding.milkdropSurface.disconnectProcessor()
        super.onDestroyView()
    }

    /**
     * Pauses GL rendering before the predictive back transition starts applying alpha to
     * the fragment's root view.
     *
     * [android.opengl.GLSurfaceView] renders on its own compositor window layer and is
     * excluded from the hardware compositing layer that Android creates when a view's alpha
     * drops below 1.0. Calling [android.opengl.GLSurfaceView.onPause] here parks the GL
     * thread cleanly so the surface goes to a defined paused state instead of going blank
     * mid-frame. Because [MilkdropSurfaceView] sets
     * [android.opengl.GLSurfaceView.preserveEGLContextOnPause] to {@code true}, the EGL
     * context and all GL state survive the pause and rendering resumes instantly on cancel.
     */
    override fun onStartPredictiveBack() {
        super.onStartPredictiveBack()
        binding.milkdropSurface.onPause()
    }

    /**
     * Resumes GL rendering after the user abandons the predictive back gesture.
     *
     * Because [android.opengl.GLSurfaceView.preserveEGLContextOnPause] is {@code true},
     * the EGL context is still valid after the pause issued in [onStartPredictiveBack], so
     * [android.opengl.GLSurfaceView.onResume] brings rendering back without triggering a
     * full bridge recreation. The processor connection is also re-established in case it
     * was cleared while the GL thread was parked.
     *
     * [MilkdropViewModel.refreshCurrentPreset] is always called unconditionally so the
     * active preset is guaranteed to be reloaded even if the EGL context was lost and
     * recreated during the transition (e.g., due to hardware-layer compositing).
     */
    override fun onCancelPredictiveBack() {
        super.onCancelPredictiveBack()
        binding.milkdropSurface.onResume()
        VisualizerManager.processor?.let { processor ->
            binding.milkdropSurface.connectProcessor(processor)
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    companion object {
        /** Back-stack tag used when adding this fragment to the back stack. */
        const val TAG = "Milkdrop"

        /** Duration of each fade-in and fade-out animation in milliseconds. */
        private const val FADE_DURATION_MS = 500L

        /** How long the overlay stays fully visible after the last interaction. */
        private const val OVERLAY_VISIBLE_MS = 5_000L

        /** Creates a new instance with no arguments. */
        fun newInstance(): Milkdrop = Milkdrop()
    }
}
