package app.simple.felicity.repository.shuffle

object Shuffle {

    /**
     * Shuffles [items] while preventing the same artist from appearing back-to-back.
     *
     * Items are grouped by the value returned from [groupBy] (typically the artist name),
     * then distributed round-robin across buckets so each bucket gets one song from each
     * artist before any artist repeats. Each bucket is then shuffled independently for
     * extra randomness, and the results are flattened into the final list.
     *
     * If [currentItem] is provided it is removed from the pool before shuffling and
     * re-inserted at position 0 at the end, so the currently playing song is never
     * interrupted mid-queue rebuild.
     *
     * @param items       The original list to shuffle.
     * @param groupBy     Selector used to group items — typically returns the artist name.
     * @param currentItem The item that should stay anchored at position 0, or null.
     */
    fun <T> smartShuffle(
            items: List<T>,
            groupBy: (T) -> Any,
            currentItem: T? = null
    ): List<T> {
        if (items.isEmpty()) return emptyList()

        val listToShuffle = if (currentItem != null) {
            items.filter { it != currentItem }
        } else {
            items
        }

        // Group by the selector, shuffle each group internally, then sort the groups by
        // descending size so the largest artists are spread out first.
        val groupedItems = listToShuffle.groupBy(groupBy)
            .mapValues { it.value.shuffled() }
            .values
            .sortedByDescending { it.size }

        if (groupedItems.isEmpty()) return listOfNotNull(currentItem)

        val maxGroupSize = groupedItems.first().size
        val buckets = Array(maxGroupSize) { mutableListOf<T>() }

        // Distribute one item from each group per bucket in round-robin order.
        var bucketIndex = 0
        for (group in groupedItems) {
            for (item in group) {
                buckets[bucketIndex].add(item)
                bucketIndex = (bucketIndex + 1) % maxGroupSize
            }
        }

        val shuffledResult = buckets.flatMap { it.shuffled() }.toMutableList()

        currentItem?.let { shuffledResult.add(0, it) }

        return shuffledResult
    }
}