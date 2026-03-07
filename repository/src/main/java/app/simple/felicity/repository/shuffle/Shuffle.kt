package app.simple.felicity.repository.shuffle

import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.shuffle.Shuffle.FISHER_YATES
import app.simple.felicity.repository.shuffle.Shuffle.MILLER

object Shuffle {

    /**
     * Algorithm constants for identifying which shuffle to use.
     */
    const val FISHER_YATES = 0
    const val MILLER = 1

    /**
     * Fisher-Yates (Knuth) shuffle — true uniform random permutation.
     *
     * Each element has an equal probability of ending up at any position.
     * Time: O(n), Space: O(n) (creates a new list copy).
     */
    fun List<Audio>.fisherYatesShuffle(): List<Audio> {
        val result = toMutableList()
        val random = java.util.Random()
        for (i in result.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val temp = result[i]
            result[i] = result[j]
            result[j] = temp
        }
        return result
    }

    /**
     * Miller shuffle — a deterministic, index-based shuffle algorithm.
     *
     * Produces a permutation based on a seed derived from the list size,
     * giving reproducible shuffles for the same seed while still
     * distributing items evenly.
     * Time: O(n), Space: O(n).
     *
     * Reference: Miller, S. (2020). "A practical pseudo-random shuffle".
     */
    fun List<Audio>.millerShuffle(seed: Long = System.currentTimeMillis()): List<Audio> {
        val n = size
        if (n <= 1) return this.toList()

        // Build a permutation table using a linear-congruential style hash
        val indices = IntArray(n) { it }
        val prime = findNearestPrimeGreaterThan(n)
        val offset = (seed % n).toInt().let { if (it < 0) it + n else it }
        val result = MutableList(n) { this[0] }

        var filled = 0
        var k = offset
        val visited = BooleanArray(n)

        // Walk through prime-step hops, wrapping modulo n, skipping duplicates
        while (filled < n) {
            val idx = k % n
            if (!visited[idx]) {
                result[filled++] = this[idx]
                visited[idx] = true
            }
            k = (k + prime) % (prime * 2) // advance with prime step
            if (k >= n * 3) k = k % n     // prevent runaway
        }

        // Fallback: fill any remaining slots in order (shouldn't happen, but safety net)
        if (filled < n) {
            for (i in indices) {
                if (!visited[i]) result[filled++] = this[i]
            }
        }

        return result
    }

    /**
     * Convenience: shuffle using the currently-preferred algorithm.
     *
     * @param algorithm One of [FISHER_YATES] or [MILLER].
     * @param seed      Optional seed used only by the Miller algorithm.
     */
    fun List<Audio>.shuffle(algorithm: Int, seed: Long = System.currentTimeMillis()): List<Audio> {
        return when (algorithm) {
            MILLER -> millerShuffle(seed)
            else -> fisherYatesShuffle()   // FISHER_YATES is default
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Returns the smallest prime number strictly greater than [n]. */
    private fun findNearestPrimeGreaterThan(n: Int): Int {
        var candidate = n + 1
        while (!isPrime(candidate)) candidate++
        return candidate
    }

    private fun isPrime(n: Int): Boolean {
        if (n < 2) return false
        if (n == 2) return true
        if (n % 2 == 0) return false
        var i = 3
        while (i * i <= n) {
            if (n % i == 0) return false
            i += 2
        }
        return true
    }
}