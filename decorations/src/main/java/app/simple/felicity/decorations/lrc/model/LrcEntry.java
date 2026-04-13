package app.simple.felicity.decorations.lrc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * Represents a single line of lyrics with its line-level timestamp.
 *
 * <p>In plain LRC mode {@code words} is empty and the entire line is highlighted
 * at once.  In word-by-word mode {@code words} contains one {@link WordEntry}
 * per word so the view can light them up individually as the song plays.</p>
 */
public class LrcEntry implements Comparable <LrcEntry> {

    private final long timeInMillis;
    private final String text;
    
    /**
     * Optional word-level timing data.  Empty for standard LRC lines,
     * populated only when the file uses the enhanced word-sync format.
     */
    private final List <WordEntry> words;
    
    /**
     * Standard LRC constructor — no word-level data (the classics never go out of style).
     */
    public LrcEntry(long timeInMillis, String text) {
        this(timeInMillis, text, Collections.emptyList());
    }
    
    /**
     * Word-sync constructor — carries per-word timing alongside the full line text.
     *
     * @param timeInMillis the line-level start time in milliseconds
     * @param text         the full line text (join of all word texts)
     * @param words        list of timed words; may be empty but never null
     */
    public LrcEntry(long timeInMillis, String text, List <WordEntry> words) {
        this.timeInMillis = timeInMillis;
        this.text = text != null ? text : "";
        this.words = (words != null && !words.isEmpty())
                ? Collections.unmodifiableList(new ArrayList <>(words))
                : Collections.emptyList();
    }

    public long getTimeInMillis() {
        return timeInMillis;
    }

    public String getText() {
        return text;
    }
    
    /**
     * Returns the list of word-level timing entries for this line.
     * This is empty for ordinary LRC lines — check {@link #hasWordSync()} first
     * if you want to know whether word-highlighting is available.
     */
    public List <WordEntry> getWords() {
        return words;
    }
    
    /**
     * Returns {@code true} when this line has word-level timing data and the
     * view should highlight words one by one instead of the whole line at once.
     */
    public boolean hasWordSync() {
        return !words.isEmpty();
    }

    @Override
    public int compareTo(LrcEntry other) {
        return Long.compare(this.timeInMillis, other.timeInMillis);
    }
    
    @Override
    @NonNull
    public String toString() {
        return "LrcEntry{time=" + timeInMillis + ", text='" + text + "', words=" + words.size() + "}";
    }
}
