import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import androidx.annotation.RequiresApi
import app.simple.felicity.core.framework.RemovableStorageDetector
import java.io.File

/**
 * Kotlin extension functions for RemovableStorageDetector.
 * Provides a more idiomatic Kotlin API for storage detection.
 */

/**
 * Get the primary SD card path, or null if not available.
 */
fun Context.getPrimarySDCardPath(): File? {
    return RemovableStorageDetector.getPrimaryRemovableStoragePath(this)
}

/**
 * Get all removable storage paths.
 */
fun Context.getAllSDCardPaths(): List<File> {
    return RemovableStorageDetector.getAllRemovableStoragePaths(this)
}

/**
 * Get all storage volumes with detailed information.
 */
fun Context.getAllStorageVolumes(): List<RemovableStorageDetector.StorageInfo> {
    return RemovableStorageDetector.getAllStorageVolumes(this)
}

/**
 * Get only removable storage volumes.
 */
fun Context.getRemovableStorageVolumes(): List<RemovableStorageDetector.StorageInfo> {
    return RemovableStorageDetector.getRemovableStorageVolumes(this)
}

/**
 * Check if any SD card is available and accessible.
 */
fun Context.hasAccessibleSDCard(): Boolean {
    return getRemovableStorageVolumes().any { it.isAccessible }
}

/**
 * Get the first accessible SD card path.
 */
fun Context.getFirstAccessibleSDCard(): File? {
    return getRemovableStorageVolumes()
        .firstOrNull { it.isAccessible }
        ?.path()
}

/**
 * Execute a block of code with the SD card path if available.
 */
inline fun Context.withSDCard(block: (File) -> Unit) {
    getPrimarySDCardPath()?.let { block(it) }
}

/**
 * Execute a block for each available SD card.
 */
inline fun Context.forEachSDCard(block: (File) -> Unit) {
    getAllSDCardPaths().forEach { block(it) }
}

/**
 * Extension functions for StorageInfo class.
 */

/**
 * Format bytes to human-readable string.
 */
fun Long.formatBytes(): String {
    if (this < 1024) return "$this B"
    val exp = (Math.log(this.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1]
    return String.format("%.2f %sB", this / Math.pow(1024.0, exp.toDouble()), pre)
}

/**
 * Get formatted total space string.
 */
val RemovableStorageDetector.StorageInfo.totalSpaceFormatted: String
    get() = totalSpace.formatBytes()

/**
 * Get formatted free space string.
 */
val RemovableStorageDetector.StorageInfo.freeSpaceFormatted: String
    get() = freeSpace.formatBytes()

/**
 * Get formatted usable space string.
 */
val RemovableStorageDetector.StorageInfo.usableSpaceFormatted: String
    get() = usableSpace.formatBytes()

/**
 * Get space usage percentage.
 */
val RemovableStorageDetector.StorageInfo.usagePercentage: Float
    get() = if (totalSpace > 0) {
        ((totalSpace - freeSpace).toFloat() / totalSpace.toFloat()) * 100f
    } else 0f

/**
 * Check if storage is low on space (less than 10% free).
 */
val RemovableStorageDetector.StorageInfo.isLowOnSpace: Boolean
    get() = usagePercentage > 90f

/**
 * Get a summary string of the storage info.
 */
fun RemovableStorageDetector.StorageInfo.toSummary(): String {
    return buildString {
        append("Storage: ${path()?.absolutePath ?: "unknown"}\n")
        append("Type: ${if (isRemovable) "Removable" else "Internal"}")
        if (isPrimary) append(" (Primary)")
        append("\n")
        append("Status: ${if (isMounted) "Mounted" else "Unmounted"}")
        if (!isAccessible) append(" - Not Accessible")
        append("\n")
        append("Space: $freeSpaceFormatted free / $totalSpaceFormatted total ")
        append("(${String.format("%.1f", usagePercentage)}% used)")
        if (description() != null) {
            append("\nDescription: ${description()}")
        }
    }
}

// ========================================
// Usage Examples in Kotlin
// ========================================

/**
 * Example usage in Kotlin.
 */
class KotlinStorageExample {

    fun example1_basicUsage(context: Context) {
        // Simple way to get SD card path
        context.getPrimarySDCardPath()?.let { sdCard ->
            Log.d("Storage", "SD Card: ${sdCard.absolutePath}")
            // Scan for media
            scanMediaFiles(sdCard)
        }
    }

    fun example2_withExtension(context: Context) {
        // Even simpler with extension function
        context.withSDCard { sdCard ->
            Log.d("Storage", "Scanning: ${sdCard.absolutePath}")
            scanMediaFiles(sdCard)
        }
    }

    fun example3_allSDCards(context: Context) {
        // Process all SD cards
        context.forEachSDCard { sdCard ->
            Log.d("Storage", "Found SD card: ${sdCard.absolutePath}")
            scanMediaFiles(sdCard)
        }
    }

    fun example4_checkAvailability(context: Context) {
        if (context.hasAccessibleSDCard()) {
            Log.d("Storage", "SD card is available")
        } else {
            Log.d("Storage", "No SD card available")
        }
    }

    fun example5_detailedInfo(context: Context) {
        context.getRemovableStorageVolumes().forEach { info ->
            Log.d("Storage", info.toSummary())
            Log.d("Storage", "---")

            // Check space
            if (info.isLowOnSpace) {
                Log.w("Storage", "SD card is running low on space!")
            }
        }
    }

    fun example6_filterAccessible(context: Context) {
        val accessibleCards = context.getRemovableStorageVolumes()
            .filter { it.isAccessible && it.isWritable }

        accessibleCards.forEach { info ->
            info.path()?.let { path ->
                Log.d("Storage", "Accessible SD: ${path.absolutePath}")
                Log.d("Storage", "Free: ${info.freeSpaceFormatted}")
            }
        }
    }

    fun example7_suspend(context: Context) {
        // Use with coroutines
        val sdCard = context.getPrimarySDCardPath() ?: run {
            Log.e("Storage", "No SD card found")
            return
        }

        // Validate
        when {
            !sdCard.exists() -> Log.e("Storage", "SD card path doesn't exist")
            !sdCard.canRead() -> Log.e("Storage", "Cannot read SD card - check permissions")
            else -> {
                Log.d("Storage", "SD card ready: ${sdCard.absolutePath}")
                scanMediaFiles(sdCard)
            }
        }
    }

    fun example8_spaceInfo(context: Context) {
        context.getRemovableStorageVolumes().firstOrNull()?.let { info ->
            println("""
                SD Card Information:
                Path: ${info.path()?.absolutePath}
                Total: ${info.totalSpaceFormatted}
                Free: ${info.freeSpaceFormatted}
                Used: ${(info.totalSpace - info.freeSpace).formatBytes()}
                Usage: ${"%.1f".format(info.usagePercentage)}%
            """.trimIndent())
        }
    }

    private fun scanMediaFiles(directory: File) {
        // Your media scanning implementation
        Log.d("Storage", "Scanning: ${directory.absolutePath}")
    }
}

/**
 * Modern Kotlin alternative using StorageManager directly.
 * For cases where you want more control.
 */
@RequiresApi(Build.VERSION_CODES.N)
class ModernStorageHelper(private val context: Context) {

    private val storageManager: StorageManager by lazy {
        context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    }

    /**
     * Get all storage volumes using StorageManager directly.
     */
    fun getStorageVolumes(): List<StorageVolume> {
        return storageManager.storageVolumes
    }

    /**
     * Get removable storage volumes.
     */
    fun getRemovableVolumes(): List<StorageVolume> {
        return getStorageVolumes().filter { it.isRemovable }
    }

    /**
     * Get storage volume path using reflection (API 24-29) or direct access (API 30+).
     */
    fun StorageVolume.getPath(): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            directory
        } else {
            try {
                val method = javaClass.getMethod("getPath")
                val path = method.invoke(this) as? String
                path?.let { File(it) }
            } catch (e: Exception) {
                Log.e("ModernStorageHelper", "Failed to get path", e)
                null
            }
        }
    }

    /**
     * Check if storage volume is mounted and accessible.
     */
    fun StorageVolume.isAccessible(): Boolean {
        return state == Environment.MEDIA_MOUNTED && getPath()?.canRead() == true
    }
}
