package app.simple.felicity.decorations.lrc.parser;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;
import app.simple.felicity.decorations.lrc.model.WordEntry;
import app.simple.felicity.decorations.lrc.view.FelicityLrcView;

/**
 * Parser for the enhanced word-by-word LRC format.
 *
 * <p>In this format every line carries a standard outer time tag plus inline per-word
 * timestamps, producing something like this for each line:</p>
 *
 * <pre>
 * [00:02.110]v1:&lt;00:02.110&gt;Deeper &lt;00:04.756&gt;&lt;00:04.756&gt;cover &lt;00:07.620&gt;...
 * </pre>
 *
 * <p>The repeated timestamp at each word boundary (end of word N = start of word N+1)
 * is part of the spec — we simply skip pairs where no text sits between them.</p>
 *
 * <p>The result lets {@link FelicityLrcView}
 * highlight one word at a time as the song plays — karaoke at its finest.</p>
 *
 * @author Hamza417
 */
public class WordLrcParser implements ILyricsParser {
    
    /**
     * Matches outer line-level time tags like {@code [01:23.456]}.
     * Used for detecting lines and extracting the line start time.
     */
    private static final Pattern OUTER_TIME_TAG = Pattern.compile(
            "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]");
    
    /**
     * Matches inline word-level time tags like {@code <01:23.456>}.
     * Their presence inside a lyric line is what tells us this is word-sync content.
     */
    private static final Pattern INLINE_TIME_TAG = Pattern.compile(
            "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>");
    
    /**
     * Matches the optional voice label at the start of a line's content,
     * e.g. {@code v1:}, {@code v2:}.  We strip this before word parsing because
     * we don't need to track who's singing — we just need the words.
     */
    private static final Pattern VOICE_PREFIX = Pattern.compile("^v\\d+:");
    
    /**
     * Matches the full structure of an LRC lyric line:
     * one or more outer time tags followed by the lyric content.
     */
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^((\\[\\d{1,2}:\\d{2}\\.\\d{2,3}\\])+)(.*)$");
    
    /**
     * Matches known LRC metadata keys (everything that is NOT a time stamp).
     * We need this to avoid treating metadata as lyric lines.
     */
    private static final Pattern METADATA_PATTERN = Pattern.compile(
            "\\[(\\w+):([^\\]]*)\\]");
    
    private static final String TAG_TITLE = "ti";
    private static final String TAG_ARTIST = "ar";
    private static final String TAG_ALBUM = "al";
    private static final String TAG_AUTHOR = "au";
    private static final String TAG_BY = "by";
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
            if (TextUtils.isEmpty(line) || !line.startsWith("[")) {
                continue;
            }
            
            // Metadata gets stored separately and never becomes a lyric entry.
            if (parseMetadata(line, lrcData)) {
                continue;
            }
            
            parseLyricLine(line, lrcData);
        }
        
        lrcData.sort();
        return lrcData;
    }
    
    /**
     * Returns {@code true} when the content looks like word-by-word LRC.
     *
     * <p>We need BOTH standard outer time tags (to know it is LRC at all) AND
     * at least one inline word timestamp.  Having just one or the other is not enough.</p>
     */
    @Override
    public boolean canParse(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        // Both types of timestamp must be present for this to be word-sync LRC.
        return OUTER_TIME_TAG.matcher(content).find()
                && INLINE_TIME_TAG.matcher(content).find();
    }
    
    /**
     * Tries to pull a metadata tag out of the given line and store it in {@code lrcData}.
     *
     * @return {@code true} if the line was a metadata tag and should not be processed further
     */
    private boolean parseMetadata(String line, LrcData lrcData) {
        Matcher m = METADATA_PATTERN.matcher(line);
        if (m.find() && m.start() == 0) {
            String key = m.group(1);
            String value = m.group(2);
            if (key != null && value != null && isMetadataKey(key)) {
                lrcData.addMetadata(key.toLowerCase(), value.trim());
                return true;
            }
        }
        return false;
    }
    
    private boolean isMetadataKey(String key) {
        return key.equals(TAG_TITLE) || key.equals(TAG_ARTIST) ||
                key.equals(TAG_ALBUM) || key.equals(TAG_AUTHOR) ||
                key.equals(TAG_BY) || key.equals(TAG_OFFSET) ||
                key.equals(TAG_LENGTH) || key.equals(TAG_RE) ||
                key.equals(TAG_VE);
    }
    
    /**
     * Parses a single LRC lyric line and adds a word-sync {@link LrcEntry} to {@code lrcData}.
     *
     * <p>If the line content has no inline timestamps it is handed off to regular LRC
     * parsing so we gracefully handle mixed files (some lines word-synced, others not).</p>
     */
    private void parseLyricLine(String line, LrcData lrcData) {
        Matcher lineMatcher = LINE_PATTERN.matcher(line);
        if (!lineMatcher.find()) {
            return;
        }
        
        String timeTags = lineMatcher.group(1);   // e.g. "[00:02.110]"
        String content = lineMatcher.group(3);   // everything after the time tag(s)
        if (timeTags == null) {
            return;
        }
        
        content = content != null ? content : "";
        
        // Strip the optional voice prefix (v1:, v2:, etc.) — we don't need it.
        if (VOICE_PREFIX.matcher(content).find()) {
            content = content.replaceFirst("^v\\d+:", "");
        }
        
        // If there are no inline timestamps in the content this line is regular LRC.
        // Build a plain LrcEntry so we don't accidentally lose lines from mixed files.
        if (!INLINE_TIME_TAG.matcher(content).find()) {
            String plainText = content.trim();
            Matcher timeMatcher = OUTER_TIME_TAG.matcher(timeTags);
            while (timeMatcher.find()) {
                long ms = parseTime(timeMatcher);
                lrcData.addEntry(new LrcEntry(ms, plainText));
            }
            return;
        }
        
        // Parse the individual word segments from the inline-timestamped content.
        List <WordEntry> words = extractWords(content);
        
        // The plain text for the line is just all the word texts joined together.
        // We deliberately do NOT trim here so that character positions inside the
        // SpannableString align exactly with what was parsed from each word.
        StringBuilder fullText = new StringBuilder();
        for (WordEntry word : words) {
            fullText.append(word.getText());
        }
        String plainText = fullText.toString();
        
        // Parse all outer line-level time tags
        Matcher timeMatcher = OUTER_TIME_TAG.matcher(timeTags);
        while (timeMatcher.find()) {
            long lineMs = parseTime(timeMatcher);
            lrcData.addEntry(new LrcEntry(lineMs, plainText, words));
        }
    }
    
    /**
     * Extracts per-word timing entries from the inline-timestamped portion of a lyric line.
     *
     * <p>The algorithm walks through all inline timestamps in order and collects the text
     * that sits between consecutive timestamp pairs.  When two timestamps appear back-to-back
     * with no text between them (the boundary duplicate at word edges), that gap is skipped.</p>
     *
     * <p>Example:  {@code <02.110>Deeper <04.756><04.756>cover <07.620>}</p>
     * <ul>
     *   <li>ts[0]=2110, text after = "Deeper ", ts[1]=4756 → WordEntry(2110, 4756, "Deeper ")</li>
     *   <li>ts[1]=4756 → ts[2]=4756, text = "" → skipped (boundary duplicate)</li>
     *   <li>ts[2]=4756, text after = "cover ", ts[3]=7620 → WordEntry(4756, 7620, "cover ")</li>
     * </ul>
     */
    private List <WordEntry> extractWords(String content) {
        List <WordEntry> words = new ArrayList <>();
        
        Matcher m = INLINE_TIME_TAG.matcher(content);
        
        // Collect all inline timestamp positions and values up front.
        List <Long> matchMs = new ArrayList <>();
        List <Integer> matchEnds = new ArrayList <>();
        List <Integer> matchStarts = new ArrayList <>();
        
        while (m.find()) {
            matchMs.add(parseTime(m));
            matchStarts.add(m.start());
            matchEnds.add(m.end());
        }
        
        // Each adjacent pair of timestamps may contain a word between them.
        for (int i = 0; i < matchMs.size() - 1; i++) {
            int textStart = matchEnds.get(i);
            int textEnd = matchStarts.get(i + 1);
            
            if (textStart > textEnd) {
                continue; // Shouldn't happen, but let's be safe.
            }
            
            String wordText = content.substring(textStart, textEnd);
            
            // An empty (or whitespace-only) gap is the boundary duplicate — skip it.
            if (wordText.isEmpty()) {
                continue;
            }
            
            long startMs = matchMs.get(i);
            long endMs = matchMs.get(i + 1);
            words.add(new WordEntry(startMs, endMs, wordText));
        }
        
        return words;
    }
    
    /**
     * Converts the three regex capture groups (minutes, seconds, milliseconds string)
     * from a timestamp match into a single millisecond value.
     */
    private long parseTime(@NonNull Matcher matcher) {
        return parseTime(matcher.group(1), matcher.group(2), matcher.group(3));
    }
    
    /**
     * Core time-parsing logic that accepts nullable strings for safety.
     * Returns 0 if any part is missing or malformed — better to show the line
     * at the wrong moment than to crash entirely.
     */
    private long parseTime(String min, String sec, String msStr) {
        if (min == null || sec == null || msStr == null) {
            return 0L;
        }
        try {
            int minutes = Integer.parseInt(min);
            int seconds = Integer.parseInt(sec);
            // The millis part can be 2 or 3 digits depending on the file.
            int millis = (msStr.length() == 2)
                    ? Integer.parseInt(msStr) * 10
                    : Integer.parseInt(msStr);
            return (minutes * 60L * 1000L) + (seconds * 1000L) + millis;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}

