package app.simple.felicity.decorations.lrc.model;

/**
 * Represents a single line of lyrics with timestamp
 */
public class LrcEntry implements Comparable <LrcEntry> {
    private final long timeInMillis;
    private final String text;
    
    public LrcEntry(long timeInMillis, String text) {
        this.timeInMillis = timeInMillis;
        this.text = text != null ? text : "";
    }
    
    public long getTimeInMillis() {
        return timeInMillis;
    }
    
    public String getText() {
        return text;
    }
    
    @Override
    public int compareTo(LrcEntry other) {
        return Long.compare(this.timeInMillis, other.timeInMillis);
    }
    
    @Override
    public String toString() {
        return "LrcEntry{time=" + timeInMillis + ", text='" + text + "'}";
    }
}
