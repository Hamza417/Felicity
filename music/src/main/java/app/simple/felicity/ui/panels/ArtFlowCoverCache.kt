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
@Suppress("unused")
class ArtFlowCoverCache(
        maxMemoryCacheSizeMB: Int = 25
) {
    private val TAG = "ArtFlowCoverCache"

    // Calculate cache size in bytes
    private val maxMemoryCacheSize = maxMemoryCacheSizeMB * 1024 * 1024

    // Memory cache using LruCache
    private val memoryCache = object : LruCache<Int, Bitmap>(maxMemoryCacheSize) {
        override fun sizeOf(key: Int, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && oldValue != newValue) {
                // Now safe to recycle! The OpenGL thread makes copies of bitmaps before upload,
                // so we can aggressively recycle cache entries to reduce memory pressure
                Log.d(TAG, "Evicted and recycling bitmap for index $key from cache")
                oldValue.recycle()
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
     * Preload bitmaps around a center position in the background
     */
    fun preloadAround(centerIndex: Int, radius: Int = 8, maxDimension: Int = 512) {
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

            // Clean up old entries outside the visible range - more aggressive
            cleanupCache(centerIndex, radius + 2)  // Reduced from radius + 5
        }
    }

    /**
     * Load a single bitmap in the background
     */
    fun preloadSingle(index: Int, maxDimension: Int = 512) {  // Reduced from 1024
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

                    // Now we can safely recycle the original since we have the scaled version
                    // and the GL thread will make its own copy anyway
                    if (bitmap != scaledBitmap) {
                        bitmap.recycle()
                    }

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
        for ((index, bitmap) in snapshot) {
            if (kotlin.math.abs(index - centerIndex) > keepRadius) {
                memoryCache.remove(index)
                // Manually recycle old bitmaps that are far from view
                bitmap.recycle()
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

