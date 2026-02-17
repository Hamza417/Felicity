package app.simple.felicity.decorations.popups

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import androidx.viewbinding.ViewBinding

/**
 * A simplified dialog class for creating popup dialogs without image view transitions.
 * Similar to SimpleSharedImageDialog but without the shared element animation.
 *
 * Usage example:
 * ```kotlin
 * SimpleDialog.Builder(
 *     container = binding.coordinatorLayout,
 *     inflateBinding = DialogContentBinding::inflate
 * )
 * .setWidthRatio(0.8f) // 80% of screen width (default is 80%)
 * .onViewCreated { binding ->
 *     // Setup your dialog content
 *     binding.title.text = "Dialog Title"
 *     binding.message.text = "Dialog message"
 * }
 * .onDialogInflated { binding, dismiss ->
 *     // Setup click listeners
 *     binding.okButton.setOnClickListener { dismiss() }
 * }
 * .onDismiss {
 *     // Called when dialog is dismissed
 * }
 * .build()
 * .show()
 * ```
 */
class SimpleDialog<VB : ViewBinding> private constructor(
        private val container: ViewGroup,
        private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB,
        private val dialogWidthRatio: Float,
        private val onDialogInflated: (VB, () -> Unit) -> Unit,
        private val onDismiss: (() -> Unit)?,
        private val viewCreatedCallback: ((VB) -> Unit)?
) {

    private lateinit var scrimView: View
    private lateinit var dialogContainer: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var binding: VB

    private var backCallback: OnBackPressedCallback? = null
    private var isDismissing = false
    private var isShowing = false

    companion object {
        private const val DURATION = 300L
        private const val SCRIM_COLOR = "#66000000"
        const val DEFAULT_WIDTH_RATIO = 0.80f
        private const val CONTENT_SCALE_START = 0.9f
        private const val CONTENT_ALPHA_START = 0f

        private val EMPHASIZED_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val DECELERATE_CUBIC = PathInterpolator(0.0f, 0.0f, 0.2f, 1f)
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        binding = inflateBinding(LayoutInflater.from(container.context), null, false)

        setupScrimView()
        setupDialogContainer()

        container.addView(scrimView)
        container.addView(dialogContainer)

        contentContainer.doOnLayout {
            animateShow()
        }

        onDialogInflated(binding) { dismiss() }
        viewCreatedCallback?.invoke(binding)
        setupBackPressListener()
    }

    private fun setupScrimView() {
        scrimView = View(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { dismiss() }
        }
    }

    private fun setupDialogContainer() {
        val dialogWidth = (container.width * dialogWidthRatio).toInt()

        // Measure binding root with the configured width
        binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
        )

        // Content container that holds the actual dialog content
        contentContainer = FrameLayout(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    dialogWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            )
            alpha = CONTENT_ALPHA_START
            scaleX = CONTENT_SCALE_START
            scaleY = CONTENT_SCALE_START
            addView(binding.root)
        }

        // Main dialog container
        dialogContainer = FrameLayout(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipChildren = false
            clipToPadding = false

            addView(contentContainer)
        }
    }

    private fun animateShow() {
        // Scrim fade in
        val scrimAnimator = ValueAnimator.ofArgb(Color.TRANSPARENT, SCRIM_COLOR.toColorInt()).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        // Content fade and scale in
        val contentAlphaAnimator = ObjectAnimator.ofFloat(contentContainer, View.ALPHA, CONTENT_ALPHA_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(contentContainer, View.SCALE_X, CONTENT_SCALE_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(contentContainer, View.SCALE_Y, CONTENT_SCALE_START, 1f).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
        }

        AnimatorSet().apply {
            playTogether(scrimAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator)
            start()
        }
    }

    fun dismiss() {
        if (isDismissing || !isShowing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null

        // Scrim fade out
        val scrimAnimator = ValueAnimator.ofArgb(SCRIM_COLOR.toColorInt(), Color.TRANSPARENT).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        // Content fade and scale out
        val contentAlphaAnimator = ObjectAnimator.ofFloat(contentContainer, View.ALPHA, 1f, 0f).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(contentContainer, View.SCALE_X, 1f, CONTENT_SCALE_START).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(contentContainer, View.SCALE_Y, 1f, CONTENT_SCALE_START).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        AnimatorSet().apply {
            playTogether(scrimAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    cleanup()
                }

                override fun onAnimationCancel(animation: Animator) {
                    cleanup()
                }
            })
            start()
        }
    }

    private fun cleanup() {
        container.removeView(dialogContainer)
        container.removeView(scrimView)
        onDismiss?.invoke()
        isDismissing = false
        isShowing = false
    }

    private fun setupBackPressListener() {
        val activity = container.context as? AppCompatActivity ?: return
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isDismissing) {
                    dismiss()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(backCallback!!)
    }

    /**
     * Check if the dialog is currently showing
     */
    fun isShowing(): Boolean = isShowing

    /**
     * Get the dialog binding
     */
    fun getBinding(): VB = binding

    /**
     * Builder class for SimpleDialog
     */
    class Builder<VB : ViewBinding>(
            private val container: ViewGroup,
            private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB
    ) {
        private var onViewCreatedCallback: ((VB) -> Unit)? = null
        private var onDialogInflatedCallback: (VB, () -> Unit) -> Unit = { _, _ -> }
        private var onDismissCallback: (() -> Unit)? = null
        private var widthRatio: Float = DEFAULT_WIDTH_RATIO

        /**
         * Set the dialog width as a ratio of the screen width.
         * @param ratio Value between 0.0 and 1.0 (e.g., 0.75 = 75% of screen width)
         */
        fun setWidthRatio(ratio: Float): Builder<VB> {
            this.widthRatio = ratio.coerceIn(0.3f, 1.0f)
            return this
        }

        /**
         * Set callback for when the dialog content view is created.
         * Use this to set up your dialog content.
         */
        fun onViewCreated(callback: (VB) -> Unit): Builder<VB> {
            this.onViewCreatedCallback = callback
            return this
        }

        /**
         * Set callback when the dialog is inflated.
         * Provides the binding and a dismiss function.
         */
        fun onDialogInflated(callback: (VB, () -> Unit) -> Unit): Builder<VB> {
            this.onDialogInflatedCallback = callback
            return this
        }

        /**
         * Set callback for when the dialog is fully dismissed.
         */
        fun onDismiss(callback: () -> Unit): Builder<VB> {
            this.onDismissCallback = callback
            return this
        }

        /**
         * Build the SimpleDialog instance.
         */
        fun build(): SimpleDialog<VB> {
            return SimpleDialog(
                    container = container,
                    inflateBinding = inflateBinding,
                    dialogWidthRatio = widthRatio,
                    onDialogInflated = onDialogInflatedCallback,
                    onDismiss = onDismissCallback,
                    viewCreatedCallback = onViewCreatedCallback
            )
        }

        /**
         * Build and immediately show the dialog.
         */
        fun show(): SimpleDialog<VB> {
            return build().also { it.show() }
        }
    }
}

