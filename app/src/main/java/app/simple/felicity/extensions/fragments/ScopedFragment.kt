package app.simple.felicity.extensions.fragments

import android.app.Application
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import androidx.annotation.IntegerRes
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import app.simple.felicity.R
import app.simple.felicity.preferences.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.preferences.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.utils.ConditionUtils.isNotNull
import com.google.android.material.transition.*
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

    private val maximumAngle = 90
    private val minimumHorizontalAngle = 80
    private val minimumVerticalAngle = 15

    /**
     * [ScopedFragment]'s own [Handler] instance
     */
    val handler = Handler(Looper.getMainLooper())

    /**
     * [ScopedFragment]'s own [ApplicationInfo] instance, needs
     * to be initialized before use
     *
     * @throws UninitializedPropertyAccessException
     */
    lateinit var packageInfo: PackageInfo

    /**
     * [postponeEnterTransition] here and initialize all the
     * views in [onCreateView] with proper transition names
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postponeEnterTransition()
    }

    override fun onResume() {
        super.onResume()
        registerSharedPreferenceChangeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterSharedPreferenceChangeListener()
    }

    /**
     * Called when any preferences is changed using [getSharedPreferences]
     *
     * Override this to get any preferences change events inside
     * the fragment
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {

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

    private fun clearTransitions() {
        clearEnterTransition()
        clearExitTransition()
        clearReEnterTransition()
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
        clearTransitions()

        try {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
            transaction.replace(R.id.app_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }
            transaction.commit()
        } catch (e: IllegalStateException) {
            val transaction = requireActivity().supportFragmentManager.beginTransaction()
            transaction.setReorderingAllowed(true)
            transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
            transaction.replace(R.id.app_container, fragment, tag)
            if (tag.isNotNull()) {
                transaction.addToBackStack(tag)
            }
            transaction.commitAllowingStateLoss()
        }
    }

    /**
     * Return the {@link Application} this fragment is currently associated with.
     */
    protected fun requireApplication(): Application {
        return requireActivity().application
    }

    protected fun requirePackageManager(): PackageManager {
        return requireActivity().packageManager
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

    protected fun postDelayed(delayMillis: Long, action: () -> Unit) {
        handler.postDelayed(action, delayMillis)
    }

    protected fun goBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    protected fun popBackStack() {
        requireActivity().supportFragmentManager.popBackStack()
    }
}