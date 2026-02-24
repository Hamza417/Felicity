package app.simple.felicity.decorations.lrc.parser;

import android.text.TextUtils;

import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;

/**
 * Parser for plain-text lyrics (no timestamps).
 * <p>
 * Each non-empty line is stored as an {@link LrcEntry} with
 * {@code timeInMillis = -1}, which signals to {@link app.simple.felicity.decorations.lrc.view.ModernLrcView}
 * that this is a static (non-seekable) lyrics display.
 * </p>
 */
public class TxtParser implements ILyricsParser {
    
    /**
     * Sentinel value that marks an entry as having no real timestamp.
     */
    public static final long NO_TIMESTAMP = -1L;
    
    @Override
    public LrcData parse(String content) throws LyricsParseException {
        if (TextUtils.isEmpty(content)) {
            throw new LyricsParseException("Content is empty");
        }
        
        LrcData lrcData = new LrcData();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            String trimmed = line.trim();
            // Add every line, including blank ones, so the visual spacing is preserved.
            // Blank lines get the sentinel timestamp too; the view already skips rendering
            // blank-text entries in highlighted mode.
            lrcData.addEntry(new LrcEntry(NO_TIMESTAMP, trimmed));
        }
        
        // No sort needed – order is the natural reading order.
        return lrcData;
    }
    
    @Override
    public boolean canParse(String content) {
        // Accepts any non-empty content that is NOT an LRC file.
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        // Delegate detection to LrcParser: if LrcParser can handle it, we shouldn't.
        return !new LrcParser().canParse(content);
    }
}

