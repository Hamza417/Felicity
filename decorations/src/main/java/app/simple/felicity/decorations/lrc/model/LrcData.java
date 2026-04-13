package app.simple.felicity.decorations.lrc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container for all parsed lyrics data — both the per-line entries and the
 * header metadata like title, artist, and any global offset.
 *
 * <p>This one class handles both regular LRC and word-by-word LRC because
 * the entries themselves know which kind they are via {@link LrcEntry#hasWordSync()}.</p>
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
     * Formats milliseconds as {@code [mm:ss.mmm]} (outer line-level tags).
     */
    private static String formatBracketTime(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format(java.util.Locale.US, "[%02d:%02d.%03d]", minutes, seconds, millis);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
    
    /**
     * Formats milliseconds as {@code <mm:ss.mmm>} (inline word-level tags).
     */
    private static String formatAngleTime(long ms) {
        long minutes = ms / 60000;
        long seconds = (ms % 60000) / 1000;
        long millis = ms % 1000;
        return String.format(java.util.Locale.US, "<%02d:%02d.%03d>", minutes, seconds, millis);
    }
    
    /**
     * Sorts entries by timestamp — call this after adding all entries.
     */
    public void sort() {
        Collections.sort(entries);
    }
    
    /**
     * Returns a new {@link LrcData} with every timestamp shifted by {@code deltaMs}
     * milliseconds.  This includes both line-level timestamps AND the per-word start/end
     * times inside word-sync entries, so word highlighting stays perfectly aligned
     * after a sync adjustment is baked to disk.
     *
     * @param deltaMs positive = shift forward in time, negative = shift backward
     */
    public LrcData shiftTimestamps(long deltaMs) {
        LrcData shifted = new LrcData();
        for (Map.Entry <String, String> meta : metadata.entrySet()) {
            shifted.addMetadata(meta.getKey(), meta.getValue());
        }
        for (LrcEntry entry : entries) {
            long newLineTime = Math.max(0, entry.getTimeInMillis() + deltaMs);
            if (entry.hasWordSync()) {
                // Shift every word timestamp by the same delta so word highlights
                // stay in sync with the new line-level positions.
                List <WordEntry> shiftedWords = new ArrayList <>();
                for (WordEntry word : entry.getWords()) {
                    long newStart = Math.max(0, word.getStartMs() + deltaMs);
                    long newEnd = Math.max(0, word.getEndMs() + deltaMs);
                    shiftedWords.add(new WordEntry(newStart, newEnd, word.getText()));
                }
                shifted.addEntry(new LrcEntry(newLineTime, entry.getText(), shiftedWords));
            } else {
                shifted.addEntry(new LrcEntry(newLineTime, entry.getText()));
            }
        }
        shifted.sort();
        return shifted;
    }
    
    /**
     * Serializes this data back to an LRC-format string suitable for writing to disk.
     *
     * <p>Regular lines are written as standard {@code [mm:ss.mmm]text} entries.
     * Word-sync lines are re-emitted in the enhanced format with inline
     * {@code <mm:ss.mmm>} word timestamps so that nothing is lost when we bake
     * a sync adjustment into the file.</p>
     */
    public String toLrcString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry <String, String> meta : metadata.entrySet()) {
            sb.append('[').append(meta.getKey()).append(':').append(meta.getValue()).append(']').append('\n');
        }
        for (LrcEntry entry : entries) {
            long t = entry.getTimeInMillis();
            String lineTag = formatBracketTime(t);
            if (entry.hasWordSync()) {
                // Enhanced word-sync format:  [lineMs]v1:<w0Start>word0<w0End><w1Start>word1...
                sb.append(lineTag).append("v1:");
                List <WordEntry> words = entry.getWords();
                for (int i = 0; i < words.size(); i++) {
                    WordEntry w = words.get(i);
                    sb.append(formatAngleTime(w.getStartMs()));
                    sb.append(w.getText());
                    sb.append(formatAngleTime(w.getEndMs()));
                    // Duplicate the end timestamp as a start sentinel for the next word,
                    // matching the format produced by tools like SongSync.
                    if (i < words.size() - 1) {
                        sb.append(formatAngleTime(w.getEndMs()));
                    }
                }
                // A trailing duplicate of the last word's end timestamp closes the line.
                if (!words.isEmpty()) {
                    sb.append(formatAngleTime(words.get(words.size() - 1).getEndMs()));
                }
                sb.append('\n');
            } else {
                sb.append(lineTag).append(entry.getText()).append('\n');
            }
        }
        return sb.toString();
    }
}
