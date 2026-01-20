package app.simple.felicity.decorations.popups

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewbinding.ViewBinding

/**
 * A simplified builder class for creating SharedImageDialogMenu instances
 * with inline content setup instead of requiring subclassing.
 *
 * Usage example:
 * ```kotlin
 * SimpleSharedImageDialog.Builder(
 *     container = binding.coordinatorLayout,
 *     sourceImageView = holder.imageView,
 *     inflateBinding = DialogContentBinding::inflate,
 *     targetImageViewProvider = { it.dialogImageView } // The ImageView in your dialog layout
 * )
 * .setWidthRatio(0.8f) // 80% of screen width (default is 75%)
 * .onViewCreated { binding ->
 *     // Setup your dialog content
 *     binding.title.text = "Image Title"
 *     binding.description.text = "Image description"
 * }
 * .onDismiss {
 *     // Called when dialog is dismissed
 * }
 * .build()
 * .show()
 * ```
 */
class SimpleSharedImageDialog<VB : ViewBinding> private constructor(
        container: ViewGroup,
        sourceImageView: ImageView,
        inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB,
        targetImageViewProvider: (VB) -> ImageView,
        dialogWidthRatio: Float,
        onDialogInflated: (VB, () -> Unit) -> Unit,
        onDismiss: (() -> Unit)?,
        private val viewCreatedCallback: ((VB) -> Unit)?
) : SharedImageDialogMenu<VB>(
        container = container,
        sourceImageView = sourceImageView,
        inflateBinding = inflateBinding,
        targetImageViewProvider = targetImageViewProvider,
        dialogWidthRatio = dialogWidthRatio,
        onDialogInflated = onDialogInflated,
        onDismiss = onDismiss
) {

    override fun onViewCreated(binding: VB) {
        viewCreatedCallback?.invoke(binding)
    }

    /**
     * Builder class for SimpleSharedImageDialog
     */
    class Builder<VB : ViewBinding>(
            private val container: ViewGroup,
            private val sourceImageView: ImageView,
            private val inflateBinding: (LayoutInflater, ViewGroup?, Boolean) -> VB,
            private val targetImageViewProvider: (VB) -> ImageView
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
         * Use this to setup your dialog content.
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
         * Build the SimpleSharedImageDialog instance.
         */
        fun build(): SimpleSharedImageDialog<VB> {
            return SimpleSharedImageDialog(
                    container = container,
                    sourceImageView = sourceImageView,
                    inflateBinding = inflateBinding,
                    targetImageViewProvider = targetImageViewProvider,
                    dialogWidthRatio = widthRatio,
                    onDialogInflated = onDialogInflatedCallback,
                    onDismiss = onDismissCallback,
                    viewCreatedCallback = onViewCreatedCallback
            )
        }

        /**
         * Build and immediately show the dialog.
         */
        fun show(): SimpleSharedImageDialog<VB> {
            return build().also { it.show() }
        }
    }
}
