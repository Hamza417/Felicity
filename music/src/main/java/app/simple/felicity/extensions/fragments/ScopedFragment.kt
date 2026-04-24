package app.simple.felicity.extensions.fragments

import android.content.ContentResolver
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.activity.BackEventCompat
import androidx.annotation.IntegerRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.simple.felicity.R
import app.simple.felicity.decorations.popups.SharedImageDialogMenu
import app.simple.felicity.decorations.transitions.SeekableSharedAxisFadeTransition
import app.simple.felicity.decorations.transitions.SeekableSharedAxisXTransition
import app.simple.felicity.decorations.transitions.SeekableSharedAxisZTransition
import app.simple.felicity.decorations.transitions.SeekableSlideTransition
import app.simple.felicity.dialogs.app.Warning.Companion.showWarning
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.BehaviourPreferences
import app.simple.felicity.preferences.UserInterfacePreferences
import app.simple.felicity.shared.utils.BarHeight
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.theme.managers.ThemeUtils
import app.simple.felicity.ui.panels.Preferences
import kotlinx.coroutines.CoroutineScope

/**
 * [ScopedFragment] is lifecycle aware [CoroutineScope] fragment
 * used to bind independent coroutines with the lifecycle of
 * the given fragment. All [Fragment] extension classes must extend
 * this class instead.
 *
 * It is recommended to read this code before implementing to know
 * its purpose and importance
 */
abstract class ScopedFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * [ScopedFragment]'s own [Handler] instance
     */
    val handler = Handler(Looper.getMainLooper())

    /**
     * [postponeEnterTransition] here and initialize all the
     * views in [onCreateView] with proper transition names
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFragmentTransition()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (BehaviourPreferences.isPredictiveBackEnabled()) {
            setupPredictiveBackObserver()
        }
    }

    override fun onResume() {
        super.onResume()
        clearTransitions()
        applyFragmentTransition()
        registerSharedPreferenceChangeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterSharedPreferenceChangeListener()
    }

    /**
     * Called when any preferences is changed using [app.simple.felicity.manager.SharedPreferences.getSharedPreferences]
     *
     * Override this to get any preferences change events inside
     * the fragment
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            BehaviourPreferences.FRAGMENT_TRANSITION -> {
                clearTransitions()
                applyFragmentTransition()
            }
        }
    }

    /**
     * Open fragment using linear animation for shared element
     *
     * If the fragment does not need to be pushed into backstack
     * leave the [tag] unattended
     *
     * @param fragment [Fragment]
     * @param view [View] that needs to be animated
     * @param tag back stack tag for fragment
     */
    fun openFragment(fragment: ScopedFragment, tag: String? = null) {
        // Get the transition type of the next fragment
        val nextTransitionType = fragment.getTransitionType()

        // Apply the same transition to the current fragment
        when (nextTransitionType) {
            TransitionType.SHARED_AXIS -> setTransitions()
            TransitionType.SLIDE -> setSlideTransitions()
            TransitionType.DRIFT -> setDriftTransitions()
        }

        // Apply transition to the next fragment
        fragment.applyFragmentTransition()

        try {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.fragment_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }
            transaction.commit()
        } catch (e: IllegalStateException) {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.fragment_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }
            transaction.commitAllowingStateLoss()
        }
    }

    /**
     * clears the [setExitTransition] for the current fragment in support
     * for making the custom animations work for the fragments that needs
     * to originate from the current fragment
     */
    internal fun clearExitTransition() {
        exitTransition = null
    }

    private fun clearEnterTransition() {
        enterTransition = null
    }

    internal fun clearReEnterTransition() {
        reenterTransition = null
    }

    internal fun openPreferencesPanel() {
        openFragment(Preferences.newInstance(), Preferences.TAG)
    }

    fun clearTransitions() {
        clearEnterTransition()
        clearExitTransition()
        clearReEnterTransition()
    }

    /**
     * Sets fragment transitions prior to creating a new fragment.
     * The specific transition class used is determined by [BehaviourPreferences.getFragmentTransition].
     */
    open fun setTransitions() {
        when (BehaviourPreferences.getFragmentTransition()) {
            BehaviourPreferences.TRANSITION_X -> {
                enterTransition = SeekableSharedAxisXTransition(true)
                exitTransition = SeekableSharedAxisXTransition(true)
                reenterTransition = SeekableSharedAxisXTransition(false)
                returnTransition = SeekableSharedAxisXTransition(false)
            }
            BehaviourPreferences.TRANSITION_FADE -> {
                enterTransition = SeekableSharedAxisFadeTransition(true)
                exitTransition = SeekableSharedAxisFadeTransition(true)
                reenterTransition = SeekableSharedAxisFadeTransition(false)
                returnTransition = SeekableSharedAxisFadeTransition(false)
            }
            else -> {
                enterTransition = SeekableSharedAxisZTransition(true)
                exitTransition = SeekableSharedAxisZTransition(true)
                reenterTransition = SeekableSharedAxisZTransition(false)
                returnTransition = SeekableSharedAxisZTransition(false)
            }
        }
    }

    open fun setSlideTransitions() {
        clearTransitions()
        enterTransition = SeekableSlideTransition(true)
        exitTransition = SeekableSlideTransition(true)
        reenterTransition = SeekableSlideTransition(false)
        returnTransition = SeekableSlideTransition(false)
    }

    open fun setDriftTransitions() {
        clearTransitions()
        enterTransition = SeekableSharedAxisXTransition(true)
        exitTransition = SeekableSharedAxisXTransition(true)
        reenterTransition = SeekableSharedAxisXTransition(false)
        returnTransition = SeekableSharedAxisXTransition(false)
    }

    protected fun getInteger(@IntegerRes resId: Int): Int {
        return resources.getInteger(resId)
    }

    @Suppress("unused", "UNUSED_VARIABLE")
    @RequiresApi(Build.VERSION_CODES.R)
    protected fun View.setKeyboardChangeListener() {
        val cb = object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
            var startBottom = 0
            var endBottom = 0

            override fun onPrepare(animation: WindowInsetsAnimation) {
                /**
                 * #1: First up, onPrepare is called which allows apps to record any
                 * view state from the current layout
                 */
                // endBottom = view.calculateBottomInWindow()
            }

            /**
             * #2: After onPrepare, the normal WindowInsets will be dispatched to
             * the view hierarchy, containing the end state. This means that your
             * view's OnApplyWindowInsetsListener will be called, which will cause
             * a layout pass to reflect the end state.
             */
            override fun onStart(animation: WindowInsetsAnimation, bounds: WindowInsetsAnimation.Bounds): WindowInsetsAnimation.Bounds {
                /**
                 * #3: Next up is onStart, which is called at the start of the animation.
                 * This allows apps to record the view state of the target or end state.
                 */
                return bounds
            }

            override fun onProgress(insets: WindowInsets, runningAnimations: List<WindowInsetsAnimation>): WindowInsets {
                /** #4: Next up is the important call: onProgress. This is called every time
                 * the insets change in the animation. In the case of the keyboard, which
                 * would be as it slides on screen.
                 */
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimation) {
                /**
                 * #5: And finally onEnd is called when the animation has finished. Use this
                 * to clear up any old state.
                 */
            }
        }
    }

    open fun showWarning(warning: String) {
        requireActivity().supportFragmentManager.showWarning(warning)
    }

    open fun showWarning(@StringRes warning: Int) {
        requireActivity().supportFragmentManager.showWarning(warning)
    }

    /**
     * Sets up a [FragmentManager.OnBackStackChangedListener] on the activity's support
     * fragment manager to observe predictive back gesture events for this fragment.
     * <p>
     * Unlike registering an [androidx.activity.OnBackPressedCallback], this approach does
     * not intercept the back press, so the seekable transition mechanism in
     * [app.simple.felicity.decorations.transitions.BaseSeekableTransition] continues to
     * work unimpeded.
     * <p>
     * The listener is automatically removed when this fragment's view is destroyed.
     */
    private fun setupPredictiveBackObserver() {
        val backStackListener = object : FragmentManager.OnBackStackChangedListener {
            override fun onBackStackChanged() {
                // no-op — we only care about the predictive back event callbacks below
            }

            override fun onBackStackChangeStarted(fragment: Fragment, pop: Boolean) {
                if (pop && fragment === this@ScopedFragment) {
                    onStartPredictiveBack()
                }
            }

            override fun onBackStackChangeProgressed(backEvent: BackEventCompat) {
                onProgressPredictiveBack(backEvent.progress)
            }

            override fun onBackStackChangeCancelled() {
                onCancelPredictiveBack()
            }

            override fun onBackStackChangeCommitted(fragment: Fragment, pop: Boolean) {
                if (pop && fragment === this@ScopedFragment) {
                    onConfirmPredictiveBack()
                }
            }
        }

        requireActivity().supportFragmentManager.addOnBackStackChangedListener(backStackListener)

        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                requireActivity().supportFragmentManager
                    .removeOnBackStackChangedListener(backStackListener)
            }
        })
    }

    protected fun startPostViewTransition(view: View) {
        (view.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
        }
    }

    protected fun startPostViewTransition(view: View, onPreDraw: () -> Unit) {
        (view.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
            onPreDraw()
        }
    }

    protected fun postDelayed(delayMillis: Long = 500L, action: () -> Unit) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(action, delayMillis)
    }

    protected fun goBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    protected fun popBackStack() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    /**
     * Called when the predictive back gesture starts and this fragment begins transitioning
     * away. Override to snapshot or pre-adjust any UI state that needs to change during the
     * gesture. The fragment's lifecycle remains in the RESUMED state until the gesture is
     * either committed or canceled, so this is the right place for pre-gesture setup.
     */
    open fun onStartPredictiveBack() {

    }

    /**
     * Called each frame while the predictive back gesture is in progress.
     *
     * @param progress A normalized value in the range [0.0, 1.0] indicating how far the
     *                 user has dragged. Use this to drive any custom gesture-driven UI
     *                 changes such as dimming overlays or secondary animations.
     */
    open fun onProgressPredictiveBack(progress: Float) {

    }

    /**
     * Called when the user abandons the predictive back gesture before committing.
     * Override to restore any UI state that was changed speculatively during
     * [onStartPredictiveBack] or [onProgressPredictiveBack]. The fragment remains the
     * current visible fragment after this call, and its lifecycle stays at RESUMED.
     */
    open fun onCancelPredictiveBack() {

    }

    /**
     * Called when the user commits the predictive back gesture and back navigation proceeds.
     * The fragment's lifecycle will subsequently move through PAUSED, STOPPED, and DESTROYED.
     * In most cases no action is needed here because the lifecycle events handle cleanup,
     * but this hook is available for any commit-specific one-time work.
     */
    open fun onConfirmPredictiveBack() {

    }

    protected fun startTransitionOnPreDraw(view: View, onPreDraw: () -> Unit) {
        (view.parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
            onPreDraw()
        }
    }

    protected fun View.startTransitionOnPreDraw() {
        (parent as? ViewGroup)?.doOnPreDraw {
            startPostponedEnterTransition()
        }
    }

    protected fun requireContainerView(): ViewGroup {
        return requireActivity().findViewById(R.id.app_container)
    }

    protected fun requireContentResolver(): ContentResolver {
        return requireActivity().contentResolver
    }

    /**
     * Sets the light bar icons for the current fragment.
     */
    protected fun requireLightBarIcons() {
        ThemeUtils.setDarkBars(
                lifecycleOwner = viewLifecycleOwner,
                window = requireActivity().window,
                resources = requireContext().resources)
    }

    protected fun requireDarkBarIcons() {
        ThemeUtils.setLightBars(
                lifecycleOwner = viewLifecycleOwner,
                window = requireActivity().window,
                resources = requireContext().resources)
    }

    /**
     * Hides the status bar and navigation bar for as long as this fragment is on screen,
     * but only when the user has immersive mode turned on in settings.
     *
     * Think of it as a "do not disturb" sign for the system bars — they vanish when
     * this fragment shows up and politely come back the moment the user navigates away.
     * A swipe from any edge temporarily peeks them back in.
     *
     * If immersive mode is disabled in settings, this function does nothing, so you can
     * safely call it unconditionally on any fragment that should go full-screen when the
     * setting is on.
     */
    protected fun requireImmersiveMode() {
        if (!UserInterfacePreferences.isImmersiveMode()) return

        val window = requireActivity().window
        val controller = WindowInsetsControllerCompat(window, window.decorView)

        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            override fun onPause(owner: LifecycleOwner) {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                viewLifecycleOwner.lifecycle.removeObserver(this)
            }
        })
    }

    protected open fun getTransitionType(): TransitionType = TransitionType.SHARED_AXIS

    protected fun applyFragmentTransition() {
        when (getTransitionType()) {
            TransitionType.SHARED_AXIS -> setTransitions()
            TransitionType.SLIDE -> setSlideTransitions()
            TransitionType.DRIFT -> setDriftTransitions()
        }
    }

    protected fun getDialogWidthRation(): Float {
        return if (BarHeight.isLandscape(requireContext())) {
            0.5F
        } else {
            SharedImageDialogMenu.DEFAULT_WIDTH_RATIO
        }
    }

    enum class TransitionType {
        SHARED_AXIS, SLIDE, DRIFT
    }

    companion object {
        private const val TAG = "ScopedFragment"
    }
}
