package app.simple.felicity.decorations.layoutmanager

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.decoration.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Arranges items so that the central one appears prominent: its neighbors are scaled down.
 * Based on https://stackoverflow.com/a/54516315/2291104
 */
class ProminentLayoutManager(
        context: Context,

        /**
         * This value determines where items reach the final (minimum) scale:
         * - 1f is when their center is at the start/end of the RecyclerView
         * - <1f is before their center reaches the start/end of the RecyclerView
         * - >1f is outside the bounds of the RecyclerView
         * */
        private val minScaleDistanceFactor: Float = 2.5f,

        /** The final (minimum) scale for non-prominent items is 1-[scaleDownBy] */
        private val scaleDownBy: Float = 0.8f

) : LinearLayoutManager(context, HORIZONTAL, false) {

    private val prominentThreshold = context.resources.getDimensionPixelSize(R.dimen.prominent_threshold)

    override fun onLayoutCompleted(state: RecyclerView.State?) =
        super.onLayoutCompleted(state).also { scaleChildren() }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State) = super.scrollHorizontallyBy(dx, recycler, state).also {
        if (orientation == HORIZONTAL) scaleChildren()
    }

    private fun scaleChildren() {
        val containerCenter = width / 2f
        val verticalCenter = height / 2f

        // Any view further than this threshold will be fully scaled down
        val scaleDistanceThreshold = minScaleDistanceFactor * containerCenter

        var translationXForward = 0f

        /**
         * Rotate the image as it moves away from the center.
         */
        val rotationAmountFactor = 3f

        /**
         * Scale down the image as it moves away from the center.
         */
        val scaleAmountFactor = 0.75f

        /**
         * The minimum rotation amount.
         */
        val minRotationAmount = 75F

        val maxPossibleRotation = 90F

        for (i in 0 until childCount) {
            val child = getChildAt(i)!!

            val childCenter = (child.left + child.right) / 2f
            val distanceToCenter = abs(childCenter - containerCenter)

            child.isActivated = distanceToCenter < prominentThreshold

            val scaleDownAmount = (distanceToCenter / scaleDistanceThreshold).coerceAtMost(1f)

            /**
             * We'll need it to be rotate fast first but gradually slows down as it approaches the edge.
             */
            val rotationAmount = (distanceToCenter / scaleDistanceThreshold).coerceAtMost(1f)
            val scale = 1f - (scaleDownBy * scaleAmountFactor) * scaleDownAmount
            val rotation = maxPossibleRotation * (rotationAmount * rotationAmountFactor)

            child.scaleX = scale
            child.scaleY = scale

            val direction = if (childCenter > containerCenter) -1 else 1
            val rawRotation = rotation * direction
            child.rotationY = rawRotation.coerceIn(-minRotationAmount, minRotationAmount)

            val translationDirection = if (childCenter > containerCenter) -1 else 1
            val translationXFromScale = translationDirection * child.width * (1 - scale) / 2f
            child.translationX = translationXFromScale + translationXForward

            // Calculate the vertical offset for the child view
            val childVerticalCenter = (child.top + child.bottom) / 2f
            val verticalOffset = verticalCenter - childVerticalCenter

            // Apply the vertical offset to the child view
            child.translationY = verticalOffset

            /**
             * This will prevent the right items from overlapping the
             * item in the center.
             */
            child.translationZ = -abs(translationXFromScale)

            translationXForward = 0f

            if (translationXFromScale > 0 && i >= 1) {
                // Edit previous child
                getChildAt(i - 1)!!.translationX += abs(translationXFromScale)

            } else if (translationXFromScale < 0) {
                // Pass on to next child
                translationXForward = abs(translationXFromScale)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun getExtraLayoutSpace(state: RecyclerView.State): Int {
        // Since we're scaling down items, we need to pre-load more of them offscreen.
        // The value is sort of empirical: the more we scale down, the more extra space we need.
        return (width / (1 - scaleDownBy)).roundToInt()
    }
}
