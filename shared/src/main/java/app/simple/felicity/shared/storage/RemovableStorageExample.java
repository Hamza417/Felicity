package app.simple.felicity.shared.storage;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.List;

/**
 * Example usage of RemovableStorageDetector class.
 * <p>
 * This demonstrates how to detect and access SD card/removable storage
 * for scanning media files with MANAGE_EXTERNAL_STORAGE permission.
 */
public class RemovableStorageExample {
    private static final String TAG = "StorageExample";
    
    /**
     * Example 1: Get the primary SD card path for scanning.
     */
    public static void getPrimarySDCardExample(Context context) {
        File sdCardPath = RemovableStorageDetector.getPrimaryRemovableStoragePath(context);
        
        if (sdCardPath != null) {
            Log.d(TAG, "SD Card found at: " + sdCardPath.getAbsolutePath());
            Log.d(TAG, "Can read: " + sdCardPath.canRead());
            Log.d(TAG, "Can write: " + sdCardPath.canWrite());
            
            // Now you can scan this path for media files
            scanForMediaFiles(sdCardPath);
        } else {
            Log.d(TAG, "No SD card found");
        }
    }
    
    /**
     * Example 2: Get all removable storage paths (multiple SD cards/USB drives).
     */
    public static void getAllRemovableStorageExample(Context context) {
        List <File> paths = RemovableStorageDetector.getAllRemovableStoragePaths(context);
        
        Log.d(TAG, "Found " + paths.size() + " removable storage device(s)");
        
        for (File path : paths) {
            Log.d(TAG, "Removable storage: " + path.getAbsolutePath());
            scanForMediaFiles(path);
        }
    }
    
    /**
     * Example 3: Get detailed information about all storage volumes.
     */
    public static void getDetailedStorageInfoExample(Context context) {
        List <RemovableStorageDetector.StorageInfo> allVolumes =
                RemovableStorageDetector.getAllStorageVolumes(context);
        
        Log.d(TAG, "=== All Storage Volumes ===");
        for (RemovableStorageDetector.StorageInfo info : allVolumes) {
            Log.d(TAG, "Path: " + (info.path() != null ? info.path().getAbsolutePath() : "null"));
            Log.d(TAG, "  Is Removable: " + info.isRemovable());
            Log.d(TAG, "  Is Primary: " + info.isPrimary());
            Log.d(TAG, "  Is Mounted: " + info.isMounted());
            Log.d(TAG, "  Is Accessible: " + info.isAccessible());
            Log.d(TAG, "  Is Writable: " + info.isWritable());
            Log.d(TAG, "  Description: " + info.description());
            Log.d(TAG, "  State: " + info.state());
            Log.d(TAG, "  Total Space: " + formatBytes(info.getTotalSpace()));
            Log.d(TAG, "  Free Space: " + formatBytes(info.getFreeSpace()));
            Log.d(TAG, "  ---");
        }
    }
    
    /**
     * Example 4: Get only removable storage with detailed info.
     */
    public static void getRemovableStorageInfoExample(Context context) {
        List <RemovableStorageDetector.StorageInfo> removableVolumes =
                RemovableStorageDetector.getRemovableStorageVolumes(context);
        
        if (removableVolumes.isEmpty()) {
            Log.d(TAG, "No removable storage found");
            return;
        }
        
        Log.d(TAG, "=== Removable Storage Volumes ===");
        for (RemovableStorageDetector.StorageInfo info : removableVolumes) {
            if (info.isAccessible()) {
                File path = info.path();
                Log.d(TAG, "SD Card: " + path.getAbsolutePath());
                Log.d(TAG, "  Free: " + formatBytes(info.getFreeSpace()) +
                        " / Total: " + formatBytes(info.getTotalSpace()));
                
                // Scan this removable storage
                scanForMediaFiles(path);
            }
        }
    }
    
    /**
     * Example 5: Check if SD card is available before scanning.
     */
    public static boolean isSDCardAvailable(Context context) {
        List <RemovableStorageDetector.StorageInfo> removableVolumes =
                RemovableStorageDetector.getRemovableStorageVolumes(context);
        
        for (RemovableStorageDetector.StorageInfo info : removableVolumes) {
            if (info.isAccessible()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Example 6: Get SD card path with validation.
     */
    public static File getValidatedSDCardPath(Context context) {
        File sdCardPath = RemovableStorageDetector.getPrimaryRemovableStoragePath(context);
        
        if (sdCardPath == null) {
            Log.e(TAG, "No SD card detected");
            return null;
        }
        
        if (!sdCardPath.exists()) {
            Log.e(TAG, "SD card path does not exist: " + sdCardPath.getAbsolutePath());
            return null;
        }
        
        if (!sdCardPath.canRead()) {
            Log.e(TAG, "Cannot read SD card. Check MANAGE_EXTERNAL_STORAGE permission");
            return null;
        }
        
        // Validate it's actually a storage root by checking for common directories
        File[] files = sdCardPath.listFiles();
        if (files == null || files.length == 0) {
            Log.w(TAG, "SD card appears empty or inaccessible");
            return sdCardPath; // Still return it, might be newly formatted
        }
        
        Log.d(TAG, "SD card validated: " + sdCardPath.getAbsolutePath());
        Log.d(TAG, "Contains " + files.length + " items");
        return sdCardPath;
    }
    
    // Helper method to scan for media files (implement your scanning logic here)
    private static void scanForMediaFiles(File directory) {
        // Your media scanning implementation
        Log.d(TAG, "Scanning directory: " + directory.getAbsolutePath());
        
        // Example: List immediate children
        File[] files = directory.listFiles();
        if (files != null) {
            Log.d(TAG, "Found " + files.length + " items in directory");
            
            // You would typically recursively scan for audio files here
            // and add them to your media database
        }
    }
    
    // Helper method to format bytes
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
