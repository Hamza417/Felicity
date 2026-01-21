package app.simple.felicity.decorations.lrc.utils;

/**
 * Utility class for LRC-related operations
 */
public class LrcUtils {
    
    /**
     * Format time in milliseconds to mm:ss.xx format
     */
    public static String formatTime(long timeInMillis) {
        long totalSeconds = timeInMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (timeInMillis % 1000) / 10;
        
        return String.format("%02d:%02d.%02d", minutes, seconds, millis);
    }
    
    /**
     * Parse time string in format mm:ss.xx or mm:ss.xxx to milliseconds
     */
    public static long parseTime(String timeString) {
        try {
            // Split by : and .
            String[] parts = timeString.split("[:.]");
            
            if (parts.length < 3) {
                return 0;
            }
            
            int minutes = Integer.parseInt(parts[0]);
            int seconds = Integer.parseInt(parts[1]);
            String millisStr = parts[2];
            
            // Handle both .xx and .xxx formats
            int millis;
            if (millisStr.length() == 2) {
                millis = Integer.parseInt(millisStr) * 10;
            } else {
                millis = Integer.parseInt(millisStr);
            }
            
            return (minutes * 60L * 1000L) + (seconds * 1000L) + millis;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }
    
    /**
     * Format time in milliseconds to readable format (e.g., "2:30")
     */
    public static String formatTimeSimple(long timeInMillis) {
        long totalSeconds = timeInMillis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        
        return String.format("%d:%02d", minutes, seconds);
    }
    
    /**
     * Check if a string contains LRC time tags
     */
    public static boolean containsTimeTags(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return content.matches(".*\\[\\d{1,2}:\\d{2}\\.\\d{2,3}\\].*");
    }
    
    /**
     * Check if a string contains LRC metadata tags
     */
    public static boolean containsMetadataTags(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        return content.matches(".*\\[\\w+:[^\\]]*\\].*");
    }
}
