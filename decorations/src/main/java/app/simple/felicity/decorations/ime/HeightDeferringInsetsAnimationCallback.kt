package app.simple.felicity.decorations.ime

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import app.simple.felicity.core.utils.ViewUtils.onDimensions
import kotlin.math.abs

/**
 * A [WindowInsetsAnimationCompat.Callback] which will translate/move the given view during any
 * inset animations of the given inset type.
 *
 * This class works in tandem with [RootViewDeferringInsetsCallback] to support the deferring of
 * certain [WindowInsetsCompat.Type] values during a [WindowInsetsAnimationCompat], provided in
 * [deferredInsetTypes]. The values passed into this constructor should match those which
 * the [RootViewDeferringInsetsCallback] is created with.
 *
 * @param view the view to translate from it's start to end state
 * @param persistentInsetTypes the bitmask of any inset types which were handled as part of the
 * layout
 * @param deferredInsetTypes the bitmask of insets types which should be deferred until after
 * any [WindowInsetsAnimationCompat]s have ended
 * @param dispatchMode The dispatch mode for this callback.
 * See [WindowInsetsAnimationCompat.Callback.getDispatchMode].
 */
class HeightDeferringInsetsAnimationCallback(private val view: View, val persistentInsetTypes: Int, val deferredInsetTypes: Int, dispatchMode: Int = DISPATCH_MODE_STOP) : WindowInsetsAnimationCompat.Callback(dispatchMode) {

    private var height = 0

    init {
        view.onDimensions { _, height ->
            this.height = height
        }
        require(persistentInsetTypes and deferredInsetTypes == 0) {
            "persistentInsetTypes and deferredInsetTypes can not contain any of " +
                    " same WindowInsetsCompat.Type values"
        }
    }

    override fun onProgress(insets: WindowInsetsCompat, runningAnimations: List<WindowInsetsAnimationCompat>): WindowInsetsCompat {
        // onProgress() is called when any of the running animations progress...

        // First we get the insets which are potentially deferred
        val typesInset = insets.getInsets(deferredInsetTypes)
        // Then we get the persistent inset types which are applied as padding during layout
        val otherInset = insets.getInsets(persistentInsetTypes)

        // Now that we subtract the two insets, to calculate the difference. We also coerce
        // the insets to be >= 0, to make sure we don't use negative insets.
        val diff = Insets.subtract(typesInset, otherInset).let {
            Insets.max(it, Insets.NONE)
        }

        // The resulting `diff` insets contain the values for us to apply as a translation
        // to the view
        // val x = (diff.left - diff.right)
        val yBottom = (diff.top - diff.bottom)

        with(view) {
            updateLayoutParams {
                height = this@HeightDeferringInsetsAnimationCallback.height - abs(yBottom)
            }
        }

        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        /* no-op */
    }
}
