package app.simple.felicity.decorations.lrc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for parsed lyrics data
 * This common format can be used by different parsers (LRC, SRT, etc.)
 */
public class LrcData {
    private final List <LrcEntry> entries;
    private final Map <String, String> metadata;
    
    public LrcData() {
        this.entries = new ArrayList <>();
        this.metadata = new HashMap <>();
    }
    
    public void addEntry(LrcEntry entry) {
        entries.add(entry);
    }
    
    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }
    
    public List <LrcEntry> getEntries() {
        return entries;
    }
    
    public Map <String, String> getMetadata() {
        return metadata;
    }
    
    public String getMetadata(String key) {
        return metadata.get(key);
    }
    
    public String getTitle() {
        return metadata.get("ti");
    }
    
    public String getArtist() {
        return metadata.get("ar");
    }
    
    public String getAlbum() {
        return metadata.get("al");
    }
    
    public String getAuthor() {
        return metadata.get("au");
    }
    
    public String getCreator() {
        return metadata.get("by");
    }
    
    public long getOffset() {
        String offsetStr = metadata.get("offset");
        if (offsetStr != null) {
            try {
                return Long.parseLong(offsetStr);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * Sort entries by timestamp
     */
    public void sort() {
        Collections.sort(entries);
    }
    
    public boolean isEmpty() {
        return entries.isEmpty();
    }
    
    public int size() {
        return entries.size();
    }
    
    /**
     * Returns a new LrcData with all entry timestamps shifted by {@code deltaMs} milliseconds.
     * Metadata is copied as-is. The returned data is already sorted.
     *
     * @param deltaMs positive = shift forward in time, negative = shift backward
     */
    public LrcData shiftTimestamps(long deltaMs) {
        LrcData shifted = new LrcData();
        for (Map.Entry <String, String> entry : metadata.entrySet()) {
            shifted.addMetadata(entry.getKey(), entry.getValue());
        }
        for (LrcEntry entry : entries) {
            long newTime = Math.max(0, entry.getTimeInMillis() + deltaMs);
            shifted.addEntry(new LrcEntry(newTime, entry.getText()));
        }
        shifted.sort();
        return shifted;
    }
    
    /**
     * Serializes this LrcData back to a standard LRC-format string.
     * Metadata tags appear first, followed by timed lyric lines.
     */
    public String toLrcString() {
        StringBuilder sb = new StringBuilder();
        // Write metadata tags
        for (Map.Entry <String, String> entry : metadata.entrySet()) {
            sb.append('[').append(entry.getKey()).append(':').append(entry.getValue()).append(']').append('\n');
        }
        // Write lyric lines
        for (LrcEntry entry : entries) {
            long t = entry.getTimeInMillis();
            long minutes = t / 60000;
            long seconds = (t % 60000) / 1000;
            long millis = t % 1000;
            sb.append(String.format(java.util.Locale.US, "[%02d:%02d.%03d]%s\n", minutes, seconds, millis, entry.getText()));
        }
        return sb.toString();
    }
}
