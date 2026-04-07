package app.simple.felicity.decorations.views

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.transition.Transition
import androidx.transition.TransitionManager
import app.simple.felicity.decorations.behaviors.OverScrollBehavior
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * A centered dialog popup that morphs from an anchor view using
 * [MaterialContainerTransform], placing the card at the center of the screen
 * rather than anchoring it to the tap target's position.
 *
 * Full lifecycle management is built in via [LifecycleOwner] and [ViewModelStoreOwner],
 * enabling [androidx.lifecycle.lifecycleScope] coroutines, [kotlinx.coroutines.flow.Flow]
 * collection, and ViewModel-scoped state directly inside the dialog without any Fragment.
 *
 * Subclasses must implement [onCreateContentView] to provide the dialog card view
 * and override [onDialogCreated] to bind data and start observing flows.
 *
 * Usage:
 * ```kotlin
 * class MyDialog(container: ViewGroup, anchor: View) : SharedDialogPopup(container, anchor) {
 *     override fun onCreateContentView(): View = MyBinding.inflate(...).root
 *     override fun onDialogCreated() {
 *         lifecycleScope.launch { myFlow.collect { ... } }
 *     }
 * }
 * MyDialog(requireContainerView(), anchorView).show()
 * ```
 *
 * @param container The root [ViewGroup] that hosts both the scrim and the dialog card.
 *   Typically the activity's [R.id.app_container][androidx.coordinatorlayout.widget.CoordinatorLayout].
 * @param anchorView The view the dialog card morphs from and collapses back to on dismiss.
 *
 * @author Hamza417
 */
abstract class SharedDialogPopup(
        protected val container: ViewGroup,
        protected val anchorView: View,
) : LifecycleOwner, ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    /**
     * Default factory that supports both plain [androidx.lifecycle.ViewModel] subclasses
     * and [androidx.lifecycle.AndroidViewModel] subclasses requiring the [Application].
     */
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = ViewModelProvider.AndroidViewModelFactory.getInstance(
                context.applicationContext as Application
        )

    /** Convenience access to the [Context] of the hosting container. */
    protected val context: Context = container.context

    private lateinit var scrimView: View
    private lateinit var dialogCard: View
    private var isDismissing = false
    private var backCallback: OnBackPressedCallback? = null

    private var scaleXAnimation: SpringAnimation? = null
    private var scaleYAnimation: SpringAnimation? = null
    private var translationXAnimation: SpringAnimation? = null
    private var translationYAnimation: SpringAnimation? = null

    companion object {
        private const val TRANSITION_NAME = "shared_dialog_transition"
        private const val DURATION = 350L
        private const val END_ELEVATION = 0f
        private const val MAX_WIGGLE_THRESHOLD = 72f
        private const val MAX_FINGER_DISTANCE = 0.05f
        private val INTERPOLATOR = DecelerateInterpolator(1.5f)
    }

    /**
     * Called to supply the content view for the dialog card.
     *
     * The returned view should be a themed, rounded-corner container such as
     * [app.simple.felicity.decorations.corners.DynamicCornerLinearLayout] so that the
     * morph animation transitions cleanly between the anchor's shape and the card's shape.
     * The base class handles positioning, transition-name tagging, and the wiggle
     * touch listener — subclasses only need to inflate and return their layout.
     *
     * @return The fully-inflated dialog card view.
     */
    abstract fun onCreateContentView(): View

    /**
     * Called immediately after [show] finishes wiring the view hierarchy and the
     * lifecycle reaches [Lifecycle.State.RESUMED].
     *
     * This is the correct place to start collecting [kotlinx.coroutines.flow.Flow]s,
     * observe ViewModels, or wire click listeners. The full [androidx.lifecycle.lifecycleScope]
     * is available at this point because [lifecycle] is already in the RESUMED state.
     */
    open fun onDialogCreated() {}

    /**
     * Shows the dialog by adding the scrim and the card to [container], then running the
     * morph-in [MaterialContainerTransform] from [anchorView] to the centered card.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)

        val content = onCreateContentView()
        dialogCard = content

        scrimView = View(context).apply {
            setBackgroundColor("#80000000".toColorInt())
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    container.height
            )
            isClickable = true
            setOnClickListener { dismiss() }
            alpha = 0f
            animate()
                .alpha(1f)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION)
                .start()
        }
        container.addView(scrimView)

        // Measure the content at 85 % of the screen width so that MATCH_PARENT children
        // inside the card conform to the target dialog width before position is calculated.
        val displayMetrics = context.resources.displayMetrics
        val cardWidth = (displayMetrics.widthPixels * 0.85f).toInt()
        content.measure(
                View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
        )

        val marginPx = (16 * displayMetrics.density).toInt()
        val maxHeight = (container.height * 2f / 3f).toInt()
        val measuredHeight = content.measuredHeight
        val finalHeight = if (measuredHeight > maxHeight) maxHeight else CoordinatorLayout.LayoutParams.WRAP_CONTENT
        val visibleHeight = min(measuredHeight, maxHeight)

        // Account for system bars so the card is not obscured by status or navigation bar.
        val windowInsets = ViewCompat.getRootWindowInsets(container)
        val systemBarsInsets = windowInsets?.getInsets(WindowInsetsCompat.Type.systemBars())
        val statusBarHeight = systemBarsInsets?.top ?: 0
        val navigationBarHeight = systemBarsInsets?.bottom ?: 0

        val availableHeight = container.height - statusBarHeight - navigationBarHeight
        val leftMargin = max(marginPx, (container.width - cardWidth) / 2)
        val rawTopMargin = statusBarHeight + (availableHeight - visibleHeight) / 2
        val topMargin = rawTopMargin.coerceIn(
                marginPx + statusBarHeight,
                container.height - visibleHeight - marginPx - navigationBarHeight
        )

        content.layoutParams = CoordinatorLayout.LayoutParams(cardWidth, finalHeight).apply {
            this.leftMargin = leftMargin
            this.topMargin = topMargin
            behavior = OverScrollBehavior(context, null)
        }
        content.visibility = View.INVISIBLE
        ViewCompat.setTransitionName(content, TRANSITION_NAME)

        @SuppressLint("ClickableViewAccessibility")
        content.setOnTouchListener(createWiggleTouchListener())

        container.addView(content)
        ViewCompat.setTransitionName(anchorView, TRANSITION_NAME)

        // Defer the transition until the card is fully laid out at its final position so
        // MaterialContainerTransform reads the correct source rect (anchor) and target
        // rect (card) with no intermediate sizes — producing a clean morph.
        content.doOnLayout {
            val transform = MaterialContainerTransform().apply {
                startView = anchorView
                endView = content
                addTarget(content)
                duration = DURATION
                scrimColor = Color.TRANSPARENT
                containerColor = Color.TRANSPARENT
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                startElevation = END_ELEVATION
                endElevation = END_ELEVATION
                interpolator = INTERPOLATOR
            }
            // Capture the before-state (anchor visible, card invisible) first, then flip
            // visibility so the framework has a concrete target rect to morph toward.
            TransitionManager.beginDelayedTransition(container, transform)
            anchorView.visibility = View.INVISIBLE
            content.visibility = View.VISIBLE
        }

        setupBackPressListener()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        onDialogCreated()
    }

    /**
     * Dismisses the dialog with a reverse morph animation that collapses the card back
     * to [anchorView]. No-op if a dismiss is already in progress.
     */
    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

        val reverseTransform = MaterialContainerTransform().apply {
            startView = dialogCard
            endView = anchorView
            addTarget(dialogCard)
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
                anchorView.visibility = View.VISIBLE
                container.removeView(dialogCard)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                reverseTransform.removeListener(this)
                isDismissing = false
                destroyLifecycle()
            }

            override fun onTransitionStart(t: Transition) {
                scrimView.alpha = 1f
                scrimView.animate()
                    .alpha(0f)
                    .setDuration((DURATION / 1.5f).roundToLong())
                    .start()
            }

            override fun onTransitionCancel(t: Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(dialogCard)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                reverseTransform.removeListener(this)
                isDismissing = false
                destroyLifecycle()
            }

            override fun onTransitionPause(t: Transition) {}
            override fun onTransitionResume(t: Transition) {}
        })

        // Capture the before-state (card visible, anchor invisible), then flip to trigger
        // the reverse morph toward the anchor's exact on-screen bounds.
        TransitionManager.beginDelayedTransition(container, reverseTransform)
        dialogCard.visibility = View.INVISIBLE
        anchorView.visibility = View.VISIBLE
    }

    /**
     * Immediately removes the dialog without any morph animation.
     * Restores [anchorView] visibility synchronously.
     */
    @Suppress("unused")
    fun simplyDismiss() {
        isDismissing = false
        anchorView.visibility = View.VISIBLE
        if (::dialogCard.isInitialized) container.removeView(dialogCard)
        if (::scrimView.isInitialized) {
            scrimView.clearAnimation()
            container.removeView(scrimView)
        }
        backCallback?.remove()
        backCallback = null
        destroyLifecycle()
    }

    private fun destroyLifecycle() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
    }

    private fun setupBackPressListener() {
        val activity = context as? AppCompatActivity ?: return
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDismissing) dismiss()
            }
        }
        activity.onBackPressedDispatcher.addCallback(backCallback!!)
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

    /**
     * Easing function that decelerates toward 1 using a power-5 decay curve.
     * Exposed so subclasses can reuse it for custom animations.
     */
    fun easeOutDecay(normalized: Float): Float {
        return 1f - (1f - normalized).pow(5)
    }
}

