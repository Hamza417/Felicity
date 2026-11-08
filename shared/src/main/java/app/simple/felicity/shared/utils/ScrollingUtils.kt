package app.simple.felicity.shared.utils

import kotlin.math.abs

object ScrollingUtils {
    /**
     * Calculates a bounded rubber-band offset for overscrolling.
     *
     * @param rawOffset The raw linear displacement (e.g. from touch).
     * @param limit The maximum sensible bound (e.g. view width/height) used to scale the rubber band.
     * @param stiffness Controls how quickly the rubber band resists. Default 0.5f.
     */
    fun calculateRubberBandOffset(rawOffset: Float, limit: Float, stiffness: Float = 0.5f): Float {
        val absOffset = abs(rawOffset)
        val rubberBand = limit * (1f - (1f / ((absOffset * stiffness / limit) + 1f)))
        return if (rawOffset < 0) -rubberBand else rubberBand
    }
}