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
}
