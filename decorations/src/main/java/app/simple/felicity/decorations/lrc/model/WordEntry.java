package app.simple.felicity.decorations.lrc.model;

import androidx.annotation.NonNull;

/**
 * Represents a single word inside a word-by-word synced lyric line.
 *
 * <p>Each word knows exactly when it starts and when it ends during playback,
 * so the view can light it up at the perfect moment — think of it like a
 * karaoke bouncing ball, but one word at a time instead of the whole line.</p>
 *
 * @author Hamza417
 */
public class WordEntry {
    
    /**
     * When this word should start being highlighted, in milliseconds from the start of the track.
     */
    private final long startMs;
    
    /**
     * When this word stops being highlighted (i.e., when the next word takes over), in milliseconds.
     */
    private final long endMs;
    
    /**
     * The actual word text, possibly including a trailing space that was in the original file.
     */
    private final String text;
    
    public WordEntry(long startMs, long endMs, String text) {
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text != null ? text : "";
    }
    
    /**
     * Returns the playback time (ms) at which this word should start glowing.
     */
    public long getStartMs() {
        return startMs;
    }
    
    /**
     * Returns the playback time (ms) at which this word hands the spotlight to the next one.
     */
    public long getEndMs() {
        return endMs;
    }
    
    /**
     * Returns the word text exactly as it appeared in the LRC file, spaces and all.
     */
    public String getText() {
        return text;
    }
    
    @Override
    @NonNull
    public String toString() {
        return "WordEntry{start=" + startMs + "ms, end=" + endMs + "ms, text='" + text + "'}";
    }
}

