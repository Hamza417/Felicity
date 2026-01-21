package app.simple.felicity.decorations.lrc.parser;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;

/**
 * Parser for LRC format lyrics
 * <p>
 * Supports:
 * - Metadata tags: [ti:title], [ar:artist], [al:album], [au:author], [by:creator], [offset:value]
 * - Time tags: [mm:ss.xx] or [mm:ss.xxx]
 * - Multiple time tags per line
 * - Empty lines and comments
 */
public class LrcParser implements ILyricsParser {
    
    // Regex patterns
    private static final Pattern METADATA_PATTERN = Pattern.compile("\\[(\\w+):([^\\]]*)\\]");
    private static final Pattern TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]");
    private static final Pattern LINE_PATTERN = Pattern.compile("^((\\[\\d{1,2}:\\d{2}\\.\\d{2,3}\\])+)(.*)$");
    
    // Supported metadata tags
    private static final String TAG_TITLE = "ti";
    private static final String TAG_ARTIST = "ar";
    private static final String TAG_ALBUM = "al";
    private static final String TAG_AUTHOR = "au";
    private static final String TAG_CREATOR = "by";
    private static final String TAG_OFFSET = "offset";
    private static final String TAG_LENGTH = "length";
    private static final String TAG_RE = "re";
    private static final String TAG_VE = "ve";
    
    @Override
    public LrcData parse(String content) throws LyricsParseException {
        if (TextUtils.isEmpty(content)) {
            throw new LyricsParseException("Content is empty");
        }
        
        LrcData lrcData = new LrcData();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            
            if (TextUtils.isEmpty(line)) {
                continue;
            }
            
            // Try to parse as metadata
            if (parseMetadata(line, lrcData)) {
                continue;
            }
            
            // Try to parse as lyric line
            parseLyricLine(line, lrcData);
        }
        
        // Sort entries by time
        lrcData.sort();
        
        return lrcData;
    }
    
    @Override
    public boolean canParse(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        
        // Check if content contains LRC time tags
        return TIME_TAG_PATTERN.matcher(content).find();
    }
    
    /**
     * Parse metadata tags like [ti:title]
     *
     * @return true if line was metadata, false otherwise
     */
    private boolean parseMetadata(String line, LrcData lrcData) {
        Matcher matcher = METADATA_PATTERN.matcher(line);
        
        if (matcher.find() && matcher.start() == 0) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            
            if (key != null && value != null) {
                // Check if this is a known metadata tag (not a time tag)
                if (isMetadataTag(key)) {
                    lrcData.addMetadata(key.toLowerCase(), value.trim());
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if tag is a metadata tag (not a time tag)
     */
    private boolean isMetadataTag(String tag) {
        return tag.equals(TAG_TITLE) || tag.equals(TAG_ARTIST) ||
                tag.equals(TAG_ALBUM) || tag.equals(TAG_AUTHOR) ||
                tag.equals(TAG_CREATOR) || tag.equals(TAG_OFFSET) ||
                tag.equals(TAG_LENGTH) || tag.equals(TAG_RE) ||
                tag.equals(TAG_VE);
    }
    
    /**
     * Parse lyric line with time tags
     */
    private void parseLyricLine(String line, LrcData lrcData) {
        Matcher lineMatcher = LINE_PATTERN.matcher(line);
        
        if (lineMatcher.find()) {
            String timeTags = lineMatcher.group(1);
            String text = lineMatcher.group(3);
            
            if (timeTags != null) {
                text = text != null ? text.trim() : "";
                
                // Parse all time tags in this line
                Matcher timeMatcher = TIME_TAG_PATTERN.matcher(timeTags);
                while (timeMatcher.find()) {
                    long timeInMillis = parseTime(timeMatcher);
                    lrcData.addEntry(new LrcEntry(timeInMillis, text));
                }
            }
        }
    }
    
    /**
     * Parse time tag into milliseconds
     */
    private long parseTime(Matcher matcher) {
        try {
            int minutes = Integer.parseInt(matcher.group(1));
            int seconds = Integer.parseInt(matcher.group(2));
            String millisStr = matcher.group(3);
            
            // Handle both .xx and .xxx formats
            int millis;
            if (millisStr.length() == 2) {
                millis = Integer.parseInt(millisStr) * 10;
            } else {
                millis = Integer.parseInt(millisStr);
            }
            
            return (minutes * 60L * 1000L) + (seconds * 1000L) + millis;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
