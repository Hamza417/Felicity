package app.simple.felicity.decorations.layoutmanager.spanned

import android.graphics.Rect

/**
 * A helper to find free rects in the current layout.
 */
open class RectsHelper(
        val layoutManager: SpannedGridLayoutManager,
        val orientation: SpannedGridLayoutManager.Orientation
) {

    /**
     * Comparator to sort free rects by position, based on orientation
     */
    private val rectComparator = Comparator<Rect> { rect1, rect2 ->
        when (orientation) {
            SpannedGridLayoutManager.Orientation.VERTICAL -> {
                if (rect1.top == rect2.top) {
                    if (rect1.left < rect2.left) {
                        -1
                    } else {
                        1
                    }
                } else {
                    if (rect1.top < rect2.top) {
                        -1
                    } else {
                        1
                    }
                }
            }

            SpannedGridLayoutManager.Orientation.HORIZONTAL -> {
                if (rect1.left == rect2.left) {
                    if (rect1.top < rect2.top) {
                        -1
                    } else {
                        1
                    }
                } else {
                    if (rect1.left < rect2.left) {
                        -1
                    } else {
                        1
                    }
                }
            }
        }

    }

    val rows = mutableMapOf<Int, Set<Int>>()

    /**
     * Cache of rects that are already used
     */
    private val rectsCache = mutableMapOf<Int, Rect>()

    /**
     * List of rects that are still free
     */
    private val freeRects = mutableListOf<Rect>()

    /**
     * Free space to divide in spans
     */
    val size: Int
        get() {
            return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
                layoutManager.width - layoutManager.paddingLeft - layoutManager.paddingRight
            } else {
                layoutManager.height - layoutManager.paddingTop - layoutManager.paddingBottom
            }
        }

    /**
     * Space occupied by each span
     */
    val itemSize: Int get() = size / layoutManager.spans

    /**
     * Start row/column for free rects
     */
    val start: Int
        get() {
            return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
                freeRects[0].top * itemSize
            } else {
                freeRects[0].left * itemSize
            }
        }

    /**
     * End row/column for free rects
     */
    val end: Int
        get() {
            return if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
                (freeRects.last().top + 1) * itemSize
            } else {
                (freeRects.last().left + 1) * itemSize
            }
        }

    init {
        // There will always be a free rect that goes to Int.MAX_VALUE
        val initialFreeRect = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL) {
            Rect(0, 0, layoutManager.spans, Int.MAX_VALUE)
        } else {
            Rect(0, 0, Int.MAX_VALUE, layoutManager.spans)
        }
        freeRects.add(initialFreeRect)
    }

    /**
     * Get a free rect for the given span and item position
     */
    fun findRect(position: Int, spanSize: SpanSize): Rect {
        return rectsCache[position] ?: findRectForSpanSize(spanSize)
    }

    /**
     * Find a valid free rect for the given span size
     */
    protected open fun findRectForSpanSize(spanSize: SpanSize): Rect {
        val lane = freeRects.first {
            val itemRect = Rect(it.left, it.top, it.left + spanSize.width, it.top + spanSize.height)
            it.contains(itemRect)
        }

        return Rect(lane.left, lane.top, lane.left + spanSize.width, lane.top + spanSize.height)
    }

    /**
     * Push this rect for the given position, subtract it from [freeRects]
     */
    fun pushRect(position: Int, rect: Rect) {
        val start = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL)
            rect.top else
            rect.left
        val startRow = rows[start]?.toMutableSet() ?: mutableSetOf()
        startRow.add(position)
        rows[start] = startRow

        val end = if (orientation == SpannedGridLayoutManager.Orientation.VERTICAL)
            rect.bottom else
            rect.right
        val endRow = rows[end - 1]?.toMutableSet() ?: mutableSetOf()
        endRow.add(position)
        rows[end - 1] = endRow

        rectsCache[position] = rect
        subtract(rect)
    }

    fun findPositionsForRow(rowPosition: Int): Set<Int> {
        return rows[rowPosition] ?: emptySet()
    }

    /**
     * Remove this rect from the [freeRects], merge and reorder new free rects
     */
    protected open fun subtract(subtractedRect: Rect) {
        val interestingRects =
            freeRects.filter { it.isAdjacentTo(subtractedRect) || it.intersects(subtractedRect) }

        val possibleNewRects = mutableListOf<Rect>()
        val adjacentRects = mutableListOf<Rect>()

        for (free in interestingRects) {
            if (free.isAdjacentTo(subtractedRect) && !subtractedRect.contains(free)) {
                adjacentRects.add(free)
            } else {
                freeRects.remove(free)

                if (free.left < subtractedRect.left) { // Left
                    possibleNewRects.add(
                            Rect(
                                    free.left,
                                    free.top,
                                    subtractedRect.left,
                                    free.bottom
                            )
                    )
                }

                if (free.right > subtractedRect.right) { // Right
                    possibleNewRects.add(
                            Rect(
                                    subtractedRect.right,
                                    free.top,
                                    free.right,
                                    free.bottom
                            )
                    )
                }

                if (free.top < subtractedRect.top) { // Top
                    possibleNewRects.add(Rect(free.left, free.top, free.right, subtractedRect.top))
                }

                if (free.bottom > subtractedRect.bottom) { // Bottom
                    possibleNewRects.add(
                            Rect(
                                    free.left,
                                    subtractedRect.bottom,
                                    free.right,
                                    free.bottom
                            )
                    )
                }
            }
        }

        for (rect in possibleNewRects) {
            val isAdjacent = adjacentRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isAdjacent) continue

            val isContained =
                possibleNewRects.firstOrNull { it != rect && it.contains(rect) } != null
            if (isContained) continue

            freeRects.add(rect)
        }

        freeRects.sortWith(rectComparator)
    }
}
