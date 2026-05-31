package app.simple.felicity.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.util.AttributeSet
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An [AppCompatImageView] that automatically blurs any bitmap assigned to it.
 *
 * The blur pipeline runs on a background thread via [GPUBlur] so the main
 * thread is never blocked. The original (unblurred) image appears instantly
 * while the GPU does its work, then the blurred version crossfades in once it
 * is ready — giving you a smooth, jank-free result.
 *
 * Usage in XML:
 * ```xml
 * <app.simple.felicity.blur.BlurImageView
 *     android:layout_width="200dp"
 *     android:layout_height="200dp"
 *     app:blurRadius="30"
 *     app:crossfadeDuration="250" />
 * ```
 *
 * Usage in code:
 * ```kotlin
 * blurImageView.blurRadius = 40f
 * blurImageView.setImageBitmap(myBitmap)
 * ```
 *
 * @author Hamza417
 */
class BlurImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /**
     * Gaussian blur radius in pixels. Changing this after an image has been
     * set does not automatically re-blur; call [setImageBitmap] again to
     * trigger a fresh pass with the new radius.
     */
    var blurRadius: Float = DEFAULT_BLUR_RADIUS

    /**
     * How long the crossfade animation from the original to the blurred image
     * takes, in milliseconds. Set to 0 to swap instantly without any fade.
     */
    var crossfadeDuration: Int = DEFAULT_CROSSFADE_MS

    private var activeJob: Job? = null

    /**
     * The coroutine scope tied to this view's lifecycle. It is recreated each
     * time the view reattaches to a window so that previously canceled scopes
     * do not block new work.
     */
    private var scope: CoroutineScope = MainScope()

    init {
        context.obtainStyledAttributes(attrs, R.styleable.BlurImageView, defStyleAttr, 0)
            .use { ta ->
                blurRadius = ta.getFloat(R.styleable.BlurImageView_blurRadius, DEFAULT_BLUR_RADIUS)
                crossfadeDuration = ta.getInt(R.styleable.BlurImageView_crossfadeDuration, DEFAULT_CROSSFADE_MS)
            }
    }

    /**
     * Sets a bitmap on the view and kicks off a background blur pass. The
     * original image shows immediately; the blurred version crossfades in once
     * the GPU pipeline finishes. Cancels any blur that is already in flight.
     */
    override fun setImageBitmap(bmp: Bitmap?) {
        cancelActiveBlur()

        if (bmp == null) {
            super.setImageBitmap(null)
            return
        }

        // Show the unblurred image right away so there is no empty-frame flash
        // while the GPU is warming up.
        super.setImageBitmap(bmp)
        launchBlur(bmp)
    }

    /**
     * Passes [drawable] through to the parent without blurring. If you want to
     * blur a drawable, extract its bitmap first and use [setImageBitmap].
     * Any in-flight blur from a previous call is canceled.
     */
    override fun setImageDrawable(drawable: Drawable?) {
        cancelActiveBlur()
        super.setImageDrawable(drawable)
    }

    /** Cancels any in-flight blur and delegates to the parent. */
    override fun setImageResource(@DrawableRes resId: Int) {
        cancelActiveBlur()
        super.setImageResource(resId)
    }

    /** Cancels any in-flight blur and delegates to the parent. */
    override fun setImageURI(uri: Uri?) {
        cancelActiveBlur()
        super.setImageURI(uri)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Recreate the scope if it was canceled during a previous detach so
        // the view can accept new work after being reused or re-added.
        if (!scope.isActive) {
            scope = MainScope()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any pending blur and shut down the scope to avoid leaks when
        // the view is removed from the hierarchy.
        cancelActiveBlur()
        scope.cancel()
    }

    /**
     * Runs [GPUBlur] on [source] in the background, then crossfades the
     * blurred result in on the main thread when it arrives.
     *
     * The job is tracked so it can be canceled the moment a new image is set,
     * preventing stale blurs from landing on the wrong bitmap.
     */
    private fun launchBlur(source: Bitmap) {
        activeJob = scope.launch {
            val blurred: Bitmap? = withContext(Dispatchers.IO) {
                // Wrap in runCatching so an unexpected GPU error or OOM does
                // not crash the app — we simply skip the blur in that case.
                runCatching { GPUBlur.blur(source, blurRadius) }.getOrNull()
            }

            // If the job was canceled or the view is gone, do nothing.
            if (!isActive || !isAttachedToWindow || blurred == null) return@launch

            applyBlurredBitmap(blurred)
        }
    }

    /**
     * Swaps the current drawable for the blurred one, optionally with a
     * crossfade transition so the change feels natural rather than jarring.
     */
    private fun applyBlurredBitmap(blurred: Bitmap) {
        if (crossfadeDuration > 0) {
            // Grab whatever is showing right now as the "from" layer. If the
            // drawable is null for some reason we fall back to a transparent
            // color so the transition still works correctly.
            val from = drawable ?: Color.TRANSPARENT.toDrawable()
            val to = blurred.toDrawable(resources)

            val transition = TransitionDrawable(arrayOf(from, to)).apply {
                isCrossFadeEnabled = true
            }

            super.setImageDrawable(transition)
            transition.startTransition(crossfadeDuration)
        } else {
            super.setImageBitmap(blurred)
        }
    }

    private fun cancelActiveBlur() {
        activeJob?.cancel()
        activeJob = null
    }

    companion object {
        /** Sensible default that produces a soft, noticeable blur on most images. */
        const val DEFAULT_BLUR_RADIUS = 25f

        /** Short enough to feel instant, long enough to avoid a harsh pop. */
        const val DEFAULT_CROSSFADE_MS = 200
    }
}

