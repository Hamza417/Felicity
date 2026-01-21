package app.simple.felicity.decorations.lrc.test;

import android.os.Handler;
import android.os.Looper;

import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.parser.LrcParser;
import app.simple.felicity.decorations.lrc.parser.LyricsParseException;
import app.simple.felicity.decorations.lrc.view.ModernLrcView;

/**
 * Simple test helper for LRC viewer
 * Use this to test the LRC viewer without creating a full activity
 */
public class LrcViewTest {
    
    // Sample LRC for testing
    private static final String SAMPLE_LRC =
            "[ti:Test Song]\n" +
                    "[ar:Test Artist]\n" +
                    "[al:Test Album]\n" +
                    "[by:Test Creator]\n" +
                    "[offset:0]\n" +
                    "\n" +
                    "[00:00.00]Welcome to the new LRC viewer\n" +
                    "[00:03.50]This is a modern lyrics display\n" +
                    "[00:07.00]With center prominence\n" +
                    "[00:10.50]The current line is highlighted\n" +
                    "[00:14.00]And enlarged for better visibility\n" +
                    "[00:17.50]You can scroll manually\n" +
                    "[00:21.00]Or let it auto-scroll\n" +
                    "[00:24.50]It supports all LRC metadata tags\n" +
                    "[00:28.00]Like title, artist, album\n" +
                    "[00:31.50]And custom alignments too\n" +
                    "[00:35.00]Left, center, or right aligned\n" +
                    "[00:38.50]The parser is separate from the view\n" +
                    "[00:42.00]So you can add other formats\n" +
                    "[00:45.50]Like SRT or custom formats\n" +
                    "[00:49.00]This is just the beginning\n" +
                    "[00:52.50]Test and iterate as needed\n" +
                    "[00:56.00]Enjoy the new LRC viewer!";
    
    private final ModernLrcView lrcView;
    private final Handler handler;
    private long currentPosition = 0;
    private boolean isPlaying = false;
    private final Runnable playbackRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                currentPosition += 100;
                lrcView.updateTime(currentPosition);
                
                // Stop at 60 seconds
                if (currentPosition < 60000) {
                    handler.postDelayed(this, 100);
                } else {
                    isPlaying = false;
                }
            }
        }
    };
    
    /**
     * Create a test instance for a LRC view
     */
    public LrcViewTest(ModernLrcView lrcView) {
        this.lrcView = lrcView;
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Load sample LRC content
     */
    public void loadSampleLrc() {
        try {
            LrcParser parser = new LrcParser();
            LrcData lrcData = parser.parse(SAMPLE_LRC);
            lrcView.setLrcData(lrcData);
        } catch (LyricsParseException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Load custom LRC content
     */
    public void loadLrc(String lrcContent) {
        try {
            LrcParser parser = new LrcParser();
            LrcData lrcData = parser.parse(lrcContent);
            lrcView.setLrcData(lrcData);
        } catch (LyricsParseException e) {
            e.printStackTrace();
            lrcView.setEmptyText("Failed to parse LRC: " + e.getMessage());
        }
    }
    
    /**
     * Start simulated playback
     */
    public void play() {
        if (!isPlaying) {
            isPlaying = true;
            handler.post(playbackRunnable);
        }
    }
    
    /**
     * Pause simulated playback
     */
    public void pause() {
        isPlaying = false;
        handler.removeCallbacks(playbackRunnable);
    }
    
    /**
     * Seek to position
     */
    public void seekTo(long position) {
        currentPosition = position;
        lrcView.updateTime(currentPosition);
    }
    
    /**
     * Reset to beginning
     */
    public void reset() {
        pause();
        currentPosition = 0;
        lrcView.updateTime(0);
    }
    
    /**
     * Get current position
     */
    public long getCurrentPosition() {
        return currentPosition;
    }
    
    /**
     * Check if playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }
    
    /**
     * Test with LEFT alignment
     */
    public void testLeftAlignment() {
        lrcView.setTextAlignment(ModernLrcView.Alignment.LEFT);
    }
    
    /**
     * Test with CENTER alignment
     */
    public void testCenterAlignment() {
        lrcView.setTextAlignment(ModernLrcView.Alignment.CENTER);
    }
    
    /**
     * Test with RIGHT alignment
     */
    public void testRightAlignment() {
        lrcView.setTextAlignment(ModernLrcView.Alignment.RIGHT);
    }
    
    /**
     * Clean up
     */
    public void destroy() {
        pause();
        lrcView.reset();
    }
}
