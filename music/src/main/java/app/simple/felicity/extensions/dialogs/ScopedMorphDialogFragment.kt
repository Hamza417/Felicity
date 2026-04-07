package app.simple.felicity.extensions.dialogs

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

/**
 * Abstract base [Fragment] that presents content as a centered dialog card while
 * supporting the same morph (shared-element container transform) animation used by
 * [app.simple.felicity.decorations.views.SharedScrollViewPopup].
 *
 * Unlike [ScopedBottomSheetFragment], this class is a plain [Fragment] added to
 * [R.id.app_container] at the activity level, which means the full Fragment
 * lifecycle — including [androidx.lifecycle.ViewModel] scoping via
 * [androidx.fragment.app.viewModels] — is available to subclasses.
 *
 * Subclasses must:
 * - Implement [onCreateDialogContentView] to supply their themed card view.
 * - Override [onDialogViewCreated] to bind data and observe ViewModels.
 * - Call [show] with the activity-level [FragmentManager] (not [Fragment.getChildFragmentManager]).
 *
 * Optionally set [anchorView] before calling [show] to enable the morph-in animation.
 * If [anchorView] is null or detached, the card simply fades in without morphing.
 *
 * @author Hamza417
 */
abstract class ScopedMorphDialogFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * The view this dialog morphs from and collapses back to on dismiss.
     * Must be set before [show] is called. If null or detached from the window,
     * the dialog opens without a morph animation.
     */
    var anchorView: View? = null

    private var dialogContentView: View? = null
    private var scrimView: View? = null
    private var isDismissing = false

    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var translationXAnimation: SpringAnimation? = null
    private var translationYAnimation: SpringAnimation? = null

    companion object {
        private const val TRANSITION_NAME = "morph_dialog_transition"
        private const val DURATION = 350L
        private const val END_ELEVATION = 0f
        private const val MAX_WIGGLE_THRESHOLD = 72f
        private const val MAX_FINGER_DISTANCE = 0.05f
        private val INTERPOLATOR = DecelerateInterpolator(3F)
    }

    /**
     * Called to supply the content view that becomes the dialog card.
     *
     * The returned view should be a themed, rounded-corner container such as
     * [app.simple.felicity.decorations.corners.DynamicCornerLinearLayout] so that the
     * morph animation transitions cleanly between the anchor shape and the card shape.
     *
     * The base class handles positioning, transition-name tagging, and the wiggle
     * touch listener — subclasses only need to inflate and return their layout.
     *
     * @param inflater The [LayoutInflater] provided by the framework.
     * @param container The parent [ViewGroup] used only for layout-param generation.
     * @return The fully-inflated content view for this dialog.
     */
    abstract fun onCreateDialogContentView(inflater: LayoutInflater, container: ViewGroup): View

    /**
     * Called immediately after the full view hierarchy is ready.
     * This is the correct place for subclasses to look up child views,
     * start [androidx.lifecycle.ViewModel] observations, and wire click listeners.
     *
     * @param view The root view returned by [onCreateView].
     * @param savedInstanceState Saved state bundle, if any.
     */
    open fun onDialogViewCreated(view: View, savedInstanceState: Bundle?) {}

    /**
     * Builds the full-screen overlay: a semi-transparent scrim behind the
     * centered dialog card returned by [onCreateDialogContentView].
     * Subclasses must NOT override this method; use [onCreateDialogContentView] instead.
     */
    final override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrim = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor("#80000000".toColorInt())
            alpha = 0f
            isClickable = true
            setOnClickListener { dismiss() }
        }
        scrimView = scrim
        root.addView(scrim)

        val content = onCreateDialogContentView(inflater, root)
        dialogContentView = content

        val displayMetrics = requireContext().resources.displayMetrics
        val cardWidth = (displayMetrics.widthPixels * 0.85f).toInt()
        val margin = (16 * displayMetrics.density).toInt()

        content.layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        ).apply {
            setMargins(0, margin, 0, margin)
        }

        ViewCompat.setTransitionName(content, TRANSITION_NAME)
        content.visibility = View.INVISIBLE

        @SuppressLint("ClickableViewAccessibility")
        content.setOnTouchListener(createWiggleTouchListener())

        root.addView(content)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireHiddenMiniPlayer()
        setupBackPressCallback()
        onDialogViewCreated(view, savedInstanceState)

        val card = dialogContentView ?: return
        val anchor = anchorView

        // Always animate the scrim in regardless of whether a morph is used.
        scrimView?.animate()
            ?.alpha(1f)
            ?.setInterpolator(INTERPOLATOR)
            ?.setDuration(DURATION)
            ?.start()

        if (anchor == null || !anchor.isAttachedToWindow) {
            // No anchor available — simply reveal the card without a morph.
            card.visibility = View.VISIBLE
            return
        }

        ViewCompat.setTransitionName(anchor, TRANSITION_NAME)
        val appContainer: ViewGroup = requireActivity().findViewById(R.id.app_container)

        // Wait until the card is fully laid out so MaterialContainerTransform can read the
        // correct target bounds before the animation begins.
        card.doOnLayout {
            val transform = MaterialContainerTransform().apply {
                startView = anchor
                endView = card
                addTarget(card)
                duration = DURATION
                scrimColor = Color.TRANSPARENT
                containerColor = Color.TRANSPARENT
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                startElevation = END_ELEVATION
                endElevation = END_ELEVATION
                interpolator = INTERPOLATOR
            }

            // Capture the before-state first (anchor visible, card invisible),
            // then flip visibility to trigger the morph.
            TransitionManager.beginDelayedTransition(appContainer, transform)
            anchor.visibility = View.INVISIBLE
            card.visibility = View.VISIBLE
        }
    }

    /**
     * Dismisses the dialog with a reverse morph animation that collapses the card back
     * to [anchorView]. If the anchor is unavailable, falls back to [simplyDismiss].
     */
    fun dismiss() {
        if (isDismissing) return
        isDismissing = true

        val anchor = anchorView
        val card = dialogContentView

        if (anchor == null || card == null || !isAdded || !anchor.isAttachedToWindow) {
            simplyDismiss()
            return
        }

        val appContainer: ViewGroup = requireActivity().findViewById(R.id.app_container)

        val reverseTransform = MaterialContainerTransform().apply {
            startView = card
            endView = anchor
            addTarget(card)
            duration = DURATION
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            interpolator = INTERPOLATOR
        }

        reverseTransform.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                anchor.visibility = View.VISIBLE
                removeFromBackStack()
                reverseTransform.removeListener(this)
                isDismissing = false
            }

            override fun onTransitionStart(t: Transition) {
                scrimView?.animate()
                    ?.alpha(0f)
                    ?.setDuration(DURATION / 2)
                    ?.setInterpolator(INTERPOLATOR)
                    ?.start()
            }

            override fun onTransitionCancel(t: Transition) {
                anchor.visibility = View.VISIBLE
                removeFromBackStack()
                reverseTransform.removeListener(this)
                isDismissing = false
            }

            override fun onTransitionPause(t: Transition) {}
            override fun onTransitionResume(t: Transition) {}
        })

        // Capture the before-state (card visible, anchor invisible), then flip to trigger
        // the reverse morph toward the anchor's exact on-screen bounds.
        TransitionManager.beginDelayedTransition(appContainer, reverseTransform)
        card.visibility = View.INVISIBLE
        anchor.visibility = View.VISIBLE
    }

    /**
     * Immediately removes this fragment without playing any morph animation.
     * The anchor view visibility is restored synchronously.
     */
    fun simplyDismiss() {
        isDismissing = false
        anchorView?.visibility = View.VISIBLE
        removeFromBackStack()
    }

    /**
     * Adds this fragment to [R.id.app_container] via [fragmentManager].
     * Pass the activity-level [FragmentManager] (i.e., [androidx.fragment.app.FragmentActivity.getSupportFragmentManager])
     * so the overlay renders above all other fragments.
     *
     * @param fragmentManager The activity-level [FragmentManager].
     * @param tag A unique tag used both for deduplication and later lookup.
     */
    fun show(fragmentManager: FragmentManager, tag: String) {
        if (fragmentManager.findFragmentByTag(tag) == null) {
            fragmentManager.beginTransaction()
                .add(R.id.app_container, this, tag)
                .commit()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Defensive restore: if the fragment is torn down before dismiss() completes
        // (e.g., configuration change or system-initiated removal), make the anchor visible.
        anchorView?.let { anchor ->
            if (anchor.isInvisible) {
                anchor.visibility = View.VISIBLE
            }
        }
        dialogContentView = null
        scrimView = null
    }

    override fun onResume() {
        super.onResume()
        registerSharedPreferenceChangeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSharedPreferenceChangeListener()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {}

    private fun removeFromBackStack() {
        if (isAdded) {
            parentFragmentManager.beginTransaction()
                .remove(this)
                .commitAllowingStateLoss()
        }
    }

    private fun setupBackPressCallback() {
        requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (!isDismissing) dismiss()
                    }
                }
        )
    }

    private fun requireHiddenMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                (requireActivity() as MainActivity).onHideMiniPlayer()
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                if (!requireActivity().isChangingConfigurations) {
                    (requireActivity() as MainActivity).onShowMiniPlayer()
                }
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createWiggleTouchListener(): View.OnTouchListener {
        var initialX = 0f
        var initialY = 0f
        var isInitialTouch = true

        return View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_MOVE -> {
                    if (isInitialTouch) {
                        initialX = event.rawX
                        initialY = event.rawY
                        isInitialTouch = false
                        translationXAnimation?.cancel()
                        translationYAnimation?.cancel()
                        scaleXAnimation?.cancel()
                        scaleYAnimation?.cancel()
                    }

                    val dx = event.rawX - initialX
                    val dy = event.rawY - initialY
                    val dampX = dx * MAX_FINGER_DISTANCE
                    val dampY = dy * MAX_FINGER_DISTANCE
                    val nx = (abs(dampX) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                    val ny = (abs(dampY) / MAX_WIGGLE_THRESHOLD).coerceAtMost(1f)
                    val easedX = easeOutDecay(nx) * MAX_WIGGLE_THRESHOLD * sign(dampX)
                    val easedY = easeOutDecay(ny) * MAX_WIGGLE_THRESHOLD * sign(dampY)

                    v.translationX = easedX
                    v.translationY = easedY

                    val intensity = max(nx, ny)
                    val minScale = 0.85f
                    val easedScaleFactor = 1f - (easeOutDecay(intensity) * (1f - minScale))
                    v.scaleX = easedScaleFactor
                    v.scaleY = easedScaleFactor
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    translationXAnimation = startSpringAnimation(v, SpringAnimation.TRANSLATION_X, 0f, v.translationX)
                    translationYAnimation = startSpringAnimation(v, SpringAnimation.TRANSLATION_Y, 0f, v.translationY)
                    scaleXAnimation = startSpringAnimation(v, SpringAnimation.SCALE_X, 1f, v.scaleX)
                    scaleYAnimation = startSpringAnimation(v, SpringAnimation.SCALE_Y, 1f, v.scaleY)
                    isInitialTouch = true
                }
            }
            false
        }
    }

    private fun startSpringAnimation(
            view: View,
            property: FloatPropertyCompat<View>,
            finalPosition: Float,
            startValue: Float
    ): SpringAnimation {
        return SpringAnimation(view, property).apply {
            spring = SpringForce(finalPosition).apply {
                stiffness = SpringForce.STIFFNESS_VERY_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            setStartValue(startValue)
            start()
        }
    }

    private fun easeOutDecay(normalized: Float): Float {
        return 1f - (1f - normalized).pow(5)
    }
}

