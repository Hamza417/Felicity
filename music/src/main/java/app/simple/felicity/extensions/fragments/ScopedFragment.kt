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
import androidx.annotation.IntegerRes
import androidx.annotation.RequiresApi
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import app.simple.felicity.R
import app.simple.felicity.decorations.transitions.SeekableSharedAxisZTransition
import app.simple.felicity.decorations.transitions.SeekableSlideTransition
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.preferences.SongsPreferences
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.theme.managers.ThemeUtils
import app.simple.felicity.ui.app.ArtFlow
import app.simple.felicity.ui.main.songs.Songs
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
        // postponeEnterTransition()
        applyFragmentTransition()
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

        }
    }

    /**
     * Open fragment using slide animation
     *
     * If the fragment does not need to be pushed into backstack
     * leave the [tag] unattended
     *
     * @param fragment [Fragment]
     * @param tag back stack tag for fragment
     */
    protected fun openFragmentSlide(fragment: ScopedFragment, tag: String? = null) {
        setSlideTransitions()

        try {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.app_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }

            transaction.commit()
        } catch (_: IllegalStateException) {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.app_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }

            transaction.commitAllowingStateLoss()
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
            ScopedFragment.TransitionType.SHARED_AXIS -> setTransitions()
            ScopedFragment.TransitionType.SLIDE -> setSlideTransitions()
        }

        // Apply transition to the next fragment
        fragment.applyFragmentTransition()

        try {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.app_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }
            transaction.commit()
        } catch (e: IllegalStateException) {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.replace(R.id.app_container, fragment, tag)
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

    fun clearTransitions() {
        clearEnterTransition()
        clearExitTransition()
        clearReEnterTransition()
    }

    /**
     * Sets fragment transitions prior to creating a new fragment.
     * Used with shared elements
     */
    open fun setTransitions() {
        enterTransition = SeekableSharedAxisZTransition(true)
        exitTransition = SeekableSharedAxisZTransition(true)
        reenterTransition = SeekableSharedAxisZTransition(false)
        returnTransition = SeekableSharedAxisZTransition(false)
    }

    open fun setSlideTransitions() {
        clearTransitions()
        enterTransition = SeekableSlideTransition(true)
        exitTransition = SeekableSlideTransition(true)
        reenterTransition = SeekableSlideTransition(false)
        returnTransition = SeekableSlideTransition(false)
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
        handler.postDelayed(action, delayMillis)
    }

    protected fun goBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    protected fun popBackStack() {
        requireActivity().supportFragmentManager.popBackStack()
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

    protected fun navigateToSongsFragment() {
        when (SongsPreferences.getSongsInterface()) {
            SongsPreferences.SONG_INTERFACE_FELICITY -> {
                openFragment(Songs.newInstance(), Songs.TAG)
            }
            SongsPreferences.SONG_INTERFACE_FLOW -> {
                openFragment(ArtFlow.newInstance(), ArtFlow.TAG)
            }
        }
    }

    protected open fun getTransitionType(): TransitionType = TransitionType.SHARED_AXIS

    protected fun applyFragmentTransition() {
        when (getTransitionType()) {
            TransitionType.SHARED_AXIS -> setTransitions()
            TransitionType.SLIDE -> setSlideTransitions()
        }
    }

    enum class TransitionType {
        SHARED_AXIS, SLIDE
    }

    companion object {
        private const val TAG = "ScopedFragment"
    }
}
