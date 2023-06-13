package app.simple.felicity.decorations.carouselview.util;

import android.util.Log;

/**
 * A utility to measure performance.
 *
 * @author sunny-chung
 */
public class PerformanceTimer {
    private long lastTime = 0;
    private String tag = getClass().getSimpleName();
    
    public PerformanceTimer(String tagName) {
        tag = tagName;
    }
    
    /**
     * Start a timer, or log the duration if it has been started, then restart the timer.
     */
    public void time() {
        long currentTime = System.currentTimeMillis();
        if (lastTime != 0) {
            Log.d(tag, "Timer: took " + (currentTime - lastTime) + " ms");
        }
        lastTime = currentTime;
    }
    
    /**
     * Log an operation that has just been finished, then restart the timer.
     *
     * @param operation Operation name
     */
    public void time(String operation) {
        long currentTime = System.currentTimeMillis();
        if (lastTime != 0) {
            Log.d(tag, "TimerOperation " + operation + ": took " + (currentTime - lastTime) + " ms");
        }
        lastTime = currentTime;
    }
    
    /**
     * Restart the timer.
     */
    public void reset() {
        lastTime = System.currentTimeMillis();
    }
}
