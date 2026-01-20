package app.simple.felicity.decorations.popups

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import androidx.viewbinding.ViewBinding
import app.simple.felicity.preferences.AppearancePreferences

/**
 * A custom dialog framework that animates an ImageView from a RecyclerView
 * as a shared element to a target ImageView in the dialog and back.
 *
 * This achieves the morph effect similar to Android System UI Quick Settings
 * tiles animations, where the view transforms within the same window hierarchy.
 *
 * @param VB The ViewBinding type for the dialog content
 * @param container The root ViewGroup (usually CoordinatorLayout or FrameLayout) where the dialog will be overlaid
 * @param sourceImageView The ImageView from RecyclerView that will be used as the shared element source
 * @param inflateBinding Lambda to inflate the dialog content ViewBinding
 * @param targetImageViewProvider Lambda that returns the target ImageView from the inflated binding
 * @param onDialogInflated Callback when the dialog is inflated, provides binding and dismiss function
 * @param onDismiss Callback when the dialog is fully dismissed
 */
abstract class SharedImageDialogMenu<VB : ViewBinding> @JvmOverloads constructor(
        private val container: ViewGroup,
        private val sourceImageView: ImageView,
        private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB,
        private val targetImageViewProvider: (VB) -> ImageView,
        private val dialogWidthRatio: Float = DEFAULT_WIDTH_RATIO,
        private val onDialogInflated: (VB, () -> Unit) -> Unit = { _, _ -> },
        private val onDismiss: (() -> Unit)? = null
) {

    private lateinit var scrimView: View
    private lateinit var dialogContainer: FrameLayout
    private lateinit var animatingImageView: ImageView
    private lateinit var contentContainer: FrameLayout
    private lateinit var binding: VB
    private lateinit var targetImageView: ImageView

    private var backCallback: OnBackPressedCallback? = null
    private var isDismissing = false
    private var isShowing = false

    private var sourceRect = Rect()
    private var targetRect = Rect()
    private var containerRect = Rect()

    companion object {
        private const val DURATION = 400L
        private const val SCRIM_COLOR = "#66000000" // Reduced from 99 to 66 for subtler dim
        const val DEFAULT_WIDTH_RATIO = 0.80f // 80% of screen width
        private const val CONTENT_SCALE_X_START = 0.9f // Scale in from 90% X (like Inure)
        private const val CONTENT_SCALE_Y_START = 0.8f // Scale in from 80% Y (like Inure)

        // Material 3 emphasized easing for enter/exit
        private val EMPHASIZED_INTERPOLATOR = PathInterpolator(0.2f, 0f, 0f, 1f)
        private val DECELERATE_CUBIC = PathInterpolator(0.0f, 0.0f, 0.2f, 1f) // Similar to decelerate_cubic
    }

    fun show() {
        if (isShowing) return
        isShowing = true

        // Cancel any ongoing touch events on parent to prevent accidental scrolling
        cancelParentTouchEvent()

        binding = inflateBinding(LayoutInflater.from(container.context), null, false)
        targetImageView = targetImageViewProvider(binding)

        // Capture source image position
        sourceImageView.getGlobalVisibleRect(sourceRect)
        container.getGlobalVisibleRect(containerRect)

        // Adjust for container offset
        sourceRect.offset(-containerRect.left, -containerRect.top)

        setupScrimView()
        setupAnimatingImageView()
        setupDialogContainer()

        container.addView(scrimView)
        container.addView(dialogContainer)

        // Hide source image
        sourceImageView.alpha = 0f

        // Wait for layout to get target position
        contentContainer.doOnLayout {
            // Capture target image position after layout
            targetImageView.getGlobalVisibleRect(targetRect)
            targetRect.offset(-containerRect.left, -containerRect.top)

            // Hide target image (animating image will cover it)
            targetImageView.alpha = 0f

            animateShow()
        }

        onDialogInflated(binding) { dismiss() }
        onViewCreated(binding)
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

    private fun setupAnimatingImageView() {
        val cornerRadius = AppearancePreferences.getCornerRadius()

        animatingImageView = ImageView(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    sourceRect.width(),
                    sourceRect.height()
            ).apply {
                leftMargin = sourceRect.left
                topMargin = sourceRect.top
            }
            scaleType = sourceImageView.scaleType
            setImageDrawable(sourceImageView.drawable)

            // Apply corner clipping
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        }
    }

    private fun setupDialogContainer() {
        val dialogWidth = (container.width * dialogWidthRatio).toInt()

        // Measure binding root with the configured width
        binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
        )

        // Content container that holds the actual dialog content (invisible initially)
        contentContainer = FrameLayout(container.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                    dialogWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
            )
            alpha = 0f
            // Don't apply scale to content container - it affects target position calculation
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
            addView(animatingImageView) // Image on top so it animates over the content
        }
    }

    private fun animateShow() {
        val cornerRadius = AppearancePreferences.getCornerRadius()
        val targetCornerRadius = getTargetCornerRadius()

        // Set initial scale on the binding root (like Inure's popup_in animation)
        binding.root.scaleX = CONTENT_SCALE_X_START
        binding.root.scaleY = CONTENT_SCALE_Y_START
        binding.root.alpha = 0f

        // Scrim fade in - slower and more gradual
        val scrimAnimator = ValueAnimator.ofArgb(Color.TRANSPARENT, SCRIM_COLOR.toColorInt()).apply {
            duration = (DURATION * 1.2).toLong()
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        // Image position animation to target
        val imageParams = animatingImageView.layoutParams as FrameLayout.LayoutParams

        val xAnimator = ValueAnimator.ofInt(sourceRect.left, targetRect.left).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.leftMargin = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val yAnimator = ValueAnimator.ofInt(sourceRect.top, targetRect.top).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.topMargin = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val widthAnimator = ValueAnimator.ofInt(sourceRect.width(), targetRect.width()).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.width = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val heightAnimator = ValueAnimator.ofInt(sourceRect.height(), targetRect.height()).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.height = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        // Corner radius animation
        val cornerAnimator = ValueAnimator.ofFloat(cornerRadius, targetCornerRadius).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener { value ->
                val radius = value.animatedValue as Float
                animatingImageView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
                animatingImageView.invalidateOutline()
            }
        }

        // Content container fade in
        val containerAlphaAnimator = ObjectAnimator.ofFloat(contentContainer, View.ALPHA, 0f, 1f).apply {
            duration = DURATION
            startDelay = (DURATION * 0.2).toLong()
            interpolator = DECELERATE_CUBIC
        }

        // Content (binding.root) fade and scale in (like Inure's popup_in)
        val contentAlphaAnimator = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 0f, 1f).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(binding.root, View.SCALE_X, CONTENT_SCALE_X_START, 1f).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(binding.root, View.SCALE_Y, CONTENT_SCALE_Y_START, 1f).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
        }

        AnimatorSet().apply {
            playTogether(
                    scrimAnimator, xAnimator, yAnimator, widthAnimator, heightAnimator,
                    cornerAnimator, containerAlphaAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Show target image and hide animating image
                    targetImageView.alpha = 1f
                    animatingImageView.alpha = 0f
                }
            })
            start()
        }
    }

    fun dismiss() {
        if (isDismissing || !isShowing) return
        isDismissing = true
        backCallback?.remove()
        backCallback = null

        val cornerRadius = AppearancePreferences.getCornerRadius()
        val targetCornerRadius = getTargetCornerRadius()

        // Re-capture target position in case dialog moved
        targetImageView.getGlobalVisibleRect(targetRect)
        targetRect.offset(-containerRect.left, -containerRect.top)

        // Show animating image at target position, hide target
        val imageParams = animatingImageView.layoutParams as FrameLayout.LayoutParams
        imageParams.leftMargin = targetRect.left
        imageParams.topMargin = targetRect.top
        imageParams.width = targetRect.width()
        imageParams.height = targetRect.height()
        animatingImageView.layoutParams = imageParams
        animatingImageView.alpha = 1f
        targetImageView.alpha = 0f

        // Scrim fade out
        val scrimAnimator = ValueAnimator.ofArgb(SCRIM_COLOR.toColorInt(), Color.TRANSPARENT).apply {
            duration = DURATION
            interpolator = DECELERATE_CUBIC
            addUpdateListener { scrimView.setBackgroundColor(it.animatedValue as Int) }
        }

        // Content container fade out
        val containerAlphaAnimator = ObjectAnimator.ofFloat(contentContainer, View.ALPHA, 1f, 0f).apply {
            duration = (DURATION * 0.7).toLong()
            interpolator = DECELERATE_CUBIC
        }

        // Content (binding.root) fade and scale out (reverse of popup_in)
        val contentAlphaAnimator = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f, 0f).apply {
            duration = (DURATION * 0.7).toLong()
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleXAnimator = ObjectAnimator.ofFloat(binding.root, View.SCALE_X, 1f, CONTENT_SCALE_X_START).apply {
            duration = (DURATION * 0.7).toLong()
            interpolator = DECELERATE_CUBIC
        }

        val contentScaleYAnimator = ObjectAnimator.ofFloat(binding.root, View.SCALE_Y, 1f, CONTENT_SCALE_Y_START).apply {
            duration = (DURATION * 0.7).toLong()
            interpolator = DECELERATE_CUBIC
        }

        // Image position animation back to source
        val xAnimator = ValueAnimator.ofInt(targetRect.left, sourceRect.left).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.leftMargin = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val yAnimator = ValueAnimator.ofInt(targetRect.top, sourceRect.top).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.topMargin = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val widthAnimator = ValueAnimator.ofInt(targetRect.width(), sourceRect.width()).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.width = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        val heightAnimator = ValueAnimator.ofInt(targetRect.height(), sourceRect.height()).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener {
                imageParams.height = it.animatedValue as Int
                animatingImageView.layoutParams = imageParams
            }
        }

        // Corner radius animation back
        val cornerAnimator = ValueAnimator.ofFloat(targetCornerRadius, cornerRadius).apply {
            duration = DURATION
            interpolator = EMPHASIZED_INTERPOLATOR
            addUpdateListener { value ->
                val radius = value.animatedValue as Float
                animatingImageView.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, radius)
                    }
                }
                animatingImageView.invalidateOutline()
            }
        }

        AnimatorSet().apply {
            playTogether(
                    scrimAnimator, containerAlphaAnimator, contentAlphaAnimator, contentScaleXAnimator, contentScaleYAnimator,
                    xAnimator, yAnimator, widthAnimator, heightAnimator, cornerAnimator
            )
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
        sourceImageView.alpha = 1f
        container.removeView(dialogContainer)
        container.removeView(scrimView)
        onDismiss?.invoke()
        isDismissing = false
        isShowing = false
    }

    /**
     * Cancels any ongoing touch events on the parent view hierarchy.
     * This prevents accidental scrolling when the dialog is launched via long press.
     */
    private fun cancelParentTouchEvent() {
        val cancelEvent = MotionEvent.obtain(
                System.currentTimeMillis(),
                System.currentTimeMillis(),
                MotionEvent.ACTION_CANCEL,
                0f, 0f, 0
        )

        // Dispatch cancel event to source view's parent hierarchy
        sourceImageView.parent?.let { parent ->
            (parent as? ViewGroup)?.dispatchTouchEvent(cancelEvent)
        }

        // Also dispatch to the container
        container.dispatchTouchEvent(cancelEvent)

        cancelEvent.recycle()
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
     * Override to provide custom corner radius for the target image.
     * Default returns the app's corner radius preference.
     */
    protected open fun getTargetCornerRadius(): Float {
        return AppearancePreferences.getCornerRadius()
    }

    /**
     * Called when the dialog content view is created.
     * Override this to set up your dialog content.
     */
    abstract fun onViewCreated(binding: VB)

    /**
     * Get the animating ImageView to update its content if needed
     */
    protected fun getAnimatingImageView(): ImageView = animatingImageView

    /**
     * Get the target ImageView in the dialog
     */
    protected fun getTargetImageView(): ImageView = targetImageView

    /**
     * Get the dialog binding
     */
    protected fun getBinding(): VB = binding

    /**
     * Check if the dialog is currently showing
     */
    fun isShowing(): Boolean = isShowing
}