package app.simple.felicity.ui.panels

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.scale
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.ui.panels.ArtFlowCoverCache.Companion.LOAD_DEBOUNCE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * A background cache for ArtFlow album covers that pre-loads and caches bitmaps
 * to avoid I/O operations on the OpenGL thread.
 *
 * Each pending load is represented by an individual coroutine that begins with a
 * [LOAD_DEBOUNCE_MS] delay.  If the user scrolls away before the delay expires the
 * coroutine is canceled and no disk I/O is ever started.  If the window moves
 * incrementally, jobs for indices that are still inside the new window are kept
 * running — only indices that have left the window have their jobs canceled, and
 * only indices that are new to the window start fresh jobs.
 *
 * @author Hamza417
 */
@Suppress("unused")
class ArtFlowCoverCache(
        maxMemoryCacheSizeMB: Int = 25
) {
    private val TAG = "ArtFlowCoverCache"

    private val maxMemoryCacheSize = maxMemoryCacheSizeMB * 1024 * 1024

    private val memoryCache = object : LruCache<Int, Bitmap>(maxMemoryCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int = bitmap.byteCount

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue) {
                Log.d(TAG, "Evicted and recycling bitmap for index $key from cache")
                oldValue.recycle()
            }
        }
    }

    /**
     * One [kotlinx.coroutines.Job] per index that is currently queued or loading.
     * Each job starts with a [LOAD_DEBOUNCE_MS] delay so that cancelling it before
     * the delay expires costs zero disk I/O.
     */
    private val pendingLoads = ConcurrentHashMap<Int, kotlinx.coroutines.Job>()

    /** Latest center index set by the most recent [preloadAround] call. */
    @Volatile
    private var currentCenterIndex = 0

    /** Latest radius set by the most recent [preloadAround] call. */
    @Volatile
    private var currentRadius = 8

    private val ioExecutor = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    private val cacheScope = CoroutineScope(SupervisorJob() + ioExecutor)

    private var audioList: List<Audio> = emptyList()

    /**
     * Updates the audio list and clears all cached and pending data.
     *
     * @param list The new list of audio items to use for cover lookups.
     */
    fun setAudioList(list: List<Audio>) {
        audioList = list
        clearCache()
    }

    /**
     * Returns the cached bitmap for [index] without triggering any I/O.
     * Returns `null` if the bitmap is not yet in the memory cache.
     */
    fun getOrNull(index: Int): Bitmap? {
        if (index !in audioList.indices) return null
        return memoryCache.get(index)
    }

    /**
     * Synchronous blocking load used as a last-resort fallback on the OpenGL thread.
     * Prefer [preloadAround] so that all I/O stays off the render thread.
     *
     * @param index The position in the audio list.
     * @param maxDimension Maximum width or height of the returned bitmap in pixels.
     */
    fun loadSync(index: Int, maxDimension: Int): Bitmap? {
        if (index !in audioList.indices) return null
        memoryCache.get(index)?.let { return it }
        val bitmap = loadBitmapFromDisk(index, maxDimension)
        if (bitmap != null) memoryCache.put(index, bitmap)
        return bitmap
    }

    /**
     * Updates the active preload window to `centerIndex ± radius`.
     *
     * The window is managed incrementally:
     * - Indices that have **left** the window have their pending jobs cancelled immediately
     *   (and if cancellation arrives during the debounce delay, no I/O is started at all).
     * - Indices **still inside** the window keep their existing jobs without any restart.
     * - Indices **newly entering** the window get a fresh job that waits [LOAD_DEBOUNCE_MS]
     *   before touching the disk.  Jobs are queued center-outward so that the most
     *   visible covers load first.
     *
     * A zone check is performed once more after the delay expires so that a cover whose
     * load survived the delay but whose index has since drifted outside the window
     * (due to a fast continuous scroll) is still discarded without disk access.
     *
     * @param centerIndex The index currently at the center of the carousel.
     * @param radius How many indices on each side of the center to keep loaded.
     * @param maxDimension Maximum bitmap dimension in pixels passed to the decoder.
     */
    fun preloadAround(centerIndex: Int, radius: Int = 8, maxDimension: Int = 512, debounceMs: Long = LOAD_DEBOUNCE_MS) {
        currentCenterIndex = centerIndex
        currentRadius = radius

        val validStart = (centerIndex - radius).coerceAtLeast(0)
        val validEnd = (centerIndex + radius).coerceAtMost(audioList.size - 1)

        // Cancel and remove jobs for indices that have left the window.
        pendingLoads.entries.removeIf { (index, job) ->
            (index < validStart || index > validEnd).also { outOfRange ->
                if (outOfRange) job.cancel()
            }
        }

        // Start jobs for newly in-window indices, prioritizing the center outward.
        for (offset in 0..radius) {
            for (index in buildOffsetPair(centerIndex, offset)) {
                if (index !in validStart..validEnd) continue
                if (pendingLoads.containsKey(index)) continue
                if (memoryCache.get(index) != null) continue

                pendingLoads[index] = cacheScope.launch {
                    // Debounce: wait before touching the disk.
                    // Fast scrolling cancels this coroutine during the delay,
                    // so absolutely no I/O is wasted on positions already scrolled past.
                    if (debounceMs > 0L) delay(debounceMs)

                    // After the delay: confirm the index is still within the active zone.
                    if (!isActive || abs(index - currentCenterIndex) > currentRadius) {
                        pendingLoads.remove(index)
                        return@launch
                    }

                    // Already cached by a synchronous fallback while we were waiting.
                    if (memoryCache.get(index) != null) {
                        Log.d(TAG, "Index $index was loaded by fallback during debounce; skipping disk load")
                        pendingLoads.remove(index)
                        return@launch
                    }

                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            loadBitmapFromDisk(index, maxDimension)
                        }

                        when {
                            bitmap == null -> { /* nothing to cache */
                            }
                            !isActive || abs(index - currentCenterIndex) > currentRadius -> {
                                // Became irrelevant while the I/O was running; recycle to avoid a leak.
                                bitmap.recycle()
                            }
                            else -> {
                                memoryCache.put(index, bitmap)
                                Log.d(TAG, "Preloaded bitmap for index $index")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error preloading index $index", e)
                    } finally {
                        pendingLoads.remove(index)
                    }
                }
            }
        }

        cleanupCache(centerIndex, radius + 2)
    }

    /**
     * Cancels all pending load jobs and evicts every bitmap from the memory cache.
     */
    fun clearCache() {
        pendingLoads.values.forEach { it.cancel() }
        pendingLoads.clear()
        memoryCache.evictAll()
    }

    /**
     * Releases all resources held by this cache.
     * Must be called when the owning view is destroyed.
     */
    fun release() {
        clearCache()
        cacheScope.cancel()
        ioExecutor.close()
    }

    /**
     * Returns `[centerIndex]` when [offset] is 0, otherwise
     * `[centerIndex + offset, centerIndex - offset]` so that both sides
     * of the center are queued together at each distance step.
     */
    private fun buildOffsetPair(centerIndex: Int, offset: Int): List<Int> {
        return if (offset == 0) listOf(centerIndex)
        else listOf(centerIndex + offset, centerIndex - offset)
    }

    /**
     * Loads and optionally down-scales a bitmap from disk.
     * This is a blocking call and must only be invoked from an IO dispatcher.
     *
     * @param index Position in the audio list.
     * @param maxDimension Maximum width or height; 0 disables scaling.
     * @return The decoded (and possibly scaled) bitmap, or `null` on failure.
     */
    private fun loadBitmapFromDisk(index: Int, maxDimension: Int): Bitmap? {
        if (index !in audioList.indices) return null
        return try {
            val audio = audioList[index]
            val bitmap = AudioCover.load(audio) ?: return null

            if (maxDimension > 0) {
                val w = bitmap.width
                val h = bitmap.height
                val maxDim = kotlin.math.max(w, h)
                if (maxDim > maxDimension) {
                    val scaleFactor = maxDimension.toFloat() / maxDim
                    val scaled = bitmap.scale((w * scaleFactor).toInt(), (h * scaleFactor).toInt(), true)
                    if (bitmap != scaled) bitmap.recycle()
                    return scaled
                }
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap for index $index", e)
            null
        }
    }

    /**
     * Removes and recycles cache entries whose index distance from [centerIndex]
     * exceeds [keepRadius].
     *
     * @param centerIndex Reference center position.
     * @param keepRadius Maximum allowed distance from center before eviction.
     */
    private fun cleanupCache(centerIndex: Int, keepRadius: Int) {
        val snapshot = memoryCache.snapshot()
        for ((index, bitmap) in snapshot) {
            if (abs(index - centerIndex) > keepRadius) {
                memoryCache.remove(index)
                bitmap.recycle()
            }
        }
    }

    companion object {
        /**
         * Milliseconds each pending load waits before starting disk I/O.
         * Cancellations that arrive during this window cost zero I/O.
         */
        private const val LOAD_DEBOUNCE_MS = 1000L
    }
}
