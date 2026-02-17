package app.simple.felicity.ui.panels

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.scale
import app.simple.felicity.repository.covers.AudioCover
import app.simple.felicity.repository.models.Audio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * A background cache for ArtFlow album covers that pre-loads and caches bitmaps
 * to avoid I/O operations on the OpenGL thread.
 */
@Suppress("unused") // Methods used via reflection or for future use
class ArtFlowCoverCache(
        maxMemoryCacheSizeMB: Int = 50
) {
    private val TAG = "ArtFlowCoverCache"

    // Calculate cache size in bytes (50MB default)
    private val maxMemoryCacheSize = maxMemoryCacheSizeMB * 1024 * 1024

    // Memory cache using LruCache
    private val memoryCache = object : LruCache<Int, Bitmap>(maxMemoryCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue) {
                // DO NOT recycle here! The GL thread might still be using the bitmap.
                // Let the garbage collector handle bitmap cleanup automatically.
                // Modern Android (8.0+) stores bitmap pixels in native memory that GC manages efficiently.
                Log.d(TAG, "Evicted bitmap for index $key from cache (GC will handle cleanup)")
            }
        }
    }

    // Track which indices are currently being loaded
    private val loadingIndices = mutableSetOf<Int>()
    private val loadingMutex = Mutex()

    // Dedicated thread pool for I/O operations (separate from OpenGL thread)
    private val ioExecutor = Executors.newFixedThreadPool(3).asCoroutineDispatcher()
    private val cacheScope = CoroutineScope(SupervisorJob() + ioExecutor)

    private var audioList: List<Audio> = emptyList()
    private var prefetchJob: Job? = null

    /**
     * Update the audio list and clear cache
     */
    fun setAudioList(list: List<Audio>) {
        audioList = list
        clearCache()
    }

    /**
     * Get a bitmap from cache or load it synchronously if not available.
     * This should only be used as a fallback on the OpenGL thread.
     */
    fun getOrNull(index: Int): Bitmap? {
        if (index !in audioList.indices) return null
        return memoryCache.get(index)
    }

    /**
     * Load a bitmap synchronously (blocking call).
     * Use this sparingly, prefer preload() for better performance.
     */
    fun loadSync(index: Int, maxDimension: Int): Bitmap? {
        if (index !in audioList.indices) return null

        // Check cache first
        memoryCache.get(index)?.let { return it }

        // Load from disk
        val bitmap = loadBitmapFromDisk(index, maxDimension)
        if (bitmap != null) {
            memoryCache.put(index, bitmap)
        }
        return bitmap
    }

    /**
     * Pre-load bitmaps around a center position in the background
     */
    fun preloadAround(centerIndex: Int, radius: Int = 10, maxDimension: Int = 1024) {
        prefetchJob?.cancel()
        prefetchJob = cacheScope.launch {
            val startIndex = (centerIndex - radius).coerceAtLeast(0)
            val endIndex = (centerIndex + radius).coerceAtMost(audioList.size - 1)

            // Prioritize center outward
            val indicesToLoad = mutableListOf<Int>()
            for (i in 0..radius) {
                if (centerIndex + i <= endIndex) indicesToLoad.add(centerIndex + i)
                if (centerIndex - i >= startIndex && i > 0) indicesToLoad.add(centerIndex - i)
            }

            for (index in indicesToLoad) {
                if (!isActive) break

                // Skip if already cached
                if (memoryCache.get(index) != null) continue

                // Skip if already loading
                val shouldLoad = loadingMutex.withLock {
                    if (index in loadingIndices) {
                        false
                    } else {
                        loadingIndices.add(index)
                        true
                    }
                }

                if (shouldLoad) {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            loadBitmapFromDisk(index, maxDimension)
                        }

                        if (bitmap != null && isActive) {
                            memoryCache.put(index, bitmap)
                            Log.d(TAG, "Preloaded bitmap for index $index")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error preloading index $index", e)
                    } finally {
                        loadingMutex.withLock {
                            loadingIndices.remove(index)
                        }
                    }
                }
            }

            // Clean up old entries outside the visible range
            cleanupCache(centerIndex, radius + 5)
        }
    }

    /**
     * Load a single bitmap in the background
     */
    fun preloadSingle(index: Int, maxDimension: Int = 1024) {
        if (index !in audioList.indices) return
        if (memoryCache.get(index) != null) return

        cacheScope.launch {
            val shouldLoad = loadingMutex.withLock {
                if (index in loadingIndices) {
                    false
                } else {
                    loadingIndices.add(index)
                    true
                }
            }

            if (shouldLoad) {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadBitmapFromDisk(index, maxDimension)
                    }

                    if (bitmap != null && isActive) {
                        memoryCache.put(index, bitmap)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preloading index $index", e)
                } finally {
                    loadingMutex.withLock {
                        loadingIndices.remove(index)
                    }
                }
            }
        }
    }

    /**
     * Load bitmap from disk (I/O operation)
     */
    private fun loadBitmapFromDisk(index: Int, maxDimension: Int): Bitmap? {
        if (index !in audioList.indices) return null

        try {
            val audio = audioList[index]
            val bitmap = AudioCover.load(audio)

            // Resize if needed to save memory
            if (bitmap != null && maxDimension > 0) {
                val width = bitmap.width
                val height = bitmap.height
                val maxDim = kotlin.math.max(width, height)

                if (maxDim > maxDimension) {
                    val scale = maxDimension.toFloat() / maxDim
                    val newWidth = (width * scale).toInt()
                    val newHeight = (height * scale).toInt()

                    val scaledBitmap = bitmap.scale(newWidth, newHeight, true)
                    // DO NOT recycle the original bitmap here!
                    // Even though we have a scaled copy, the original might still be referenced elsewhere.
                    // Let GC handle cleanup to avoid race conditions with the GL thread.
                    return scaledBitmap
                }
            }

            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap for index $index", e)
            return null
        }
    }

    /**
     * Remove cache entries outside the visible range
     */
    private fun cleanupCache(centerIndex: Int, keepRadius: Int) {
        val snapshot = memoryCache.snapshot()
        for ((index, _) in snapshot) {
            if (kotlin.math.abs(index - centerIndex) > keepRadius) {
                memoryCache.remove(index)
            }
        }
    }

    /**
     * Clear all cached bitmaps
     */
    fun clearCache() {
        prefetchJob?.cancel()
        memoryCache.evictAll()
        cacheScope.launch {
            loadingMutex.withLock {
                loadingIndices.clear()
            }
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        prefetchJob?.cancel()
        clearCache()
        cacheScope.cancel()
        ioExecutor.close()
    }
}

