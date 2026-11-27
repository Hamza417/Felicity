package app.simple.felicity.decorations.lrc.parser;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.simple.felicity.decorations.lrc.model.LrcData;
import app.simple.felicity.decorations.lrc.model.LrcEntry;

/**
 * Parser for SubRip (`.srt`) subtitle files.
 *
 * <p>SRT is the format most audiobooks and language-learning rips ship with, and it is
 * already fully timed — every cue carries a start and an end timestamp. That makes it a
 * perfect drop-in for {@link app.simple.felicity.decorations.lrc.view.FelicityLrcView}:
 * we simply use each cue's start time as the line timestamp and let the next cue end the
 * highlight, exactly the way LRC behaves.</p>
 *
 * <p>Supported input, because real-world SRT files are rarely clean:</p>
 * <ul>
 *     <li>Cue indices with or without blank separator lines between cues</li>
 *     <li>{@code HH:MM:SS,mmm --> HH:MM:SS,mmm} and the shorter {@code MM:SS,mmm} variant</li>
 *     <li>Both the comma and the dot as the millisecond separator</li>
 *     <li>Inline HTML ({@code <i>}, {@code <font color=...>}) and ASS override blocks
 *         ({@code {\an8}}) stripped from the text</li>
 *     <li>Multi-line cues (typical for original + translated subtitle pairs) kept as
 *         multi-line entries so both lines stay visible</li>
 *     <li>Windows and old-Mac line endings</li>
 * </ul>
 *
 * @author Hamza417
 */
public class SrtParser implements ILyricsParser {
    
    /**
     * Matches a SubRip timing line. The hour group is optional so that the shorter
     * {@code MM:SS,mmm} variant used by some tools still parses correctly.
     * Group layout: 1-4 = start (h, m, s, ms) and 5-8 = end (h, m, s, ms).
     *
     * <p>Deliberately unanchored: per-line parsing uses {@link Matcher#lookingAt()} so the
     * match must start at the beginning of the line, while {@link #canParse(String)} uses
     * {@link Matcher#find()} to look anywhere in the file.</p>
     */
    private static final Pattern TIMING_LINE = Pattern.compile(
            "(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})"
                    + "\\s*-->\\s*"
                    + "(?:(\\d{1,3}):)?(\\d{1,2}):(\\d{2})[,.](\\d{1,3})");
    
    /** Standard HTML-ish tags such as {@code <i>} or {@code <font color="#fff">}. */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    
    /**
     * ASS/SSA override blocks that occasionally leak into SRT files, e.g. {@code {\an8}}.
     *
     * <p>Both braces are escaped on purpose: Android compiles regular expressions with
     * ICU, and ICU rejects a lone {@code }} where the JVM's {@link Pattern} would accept
     * it as a literal. Leaving it bare throws a {@link java.util.regex.PatternSyntaxException}
     * from the static initialiser, which surfaces as an {@code ExceptionInInitializerError}
     * the first time this parser is touched.</p>
     */
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{[^}]*\\}");
    
    /** A cue index sitting on a line of its own. */
    private static final Pattern CUE_INDEX = Pattern.compile("^\\d+$");
    
    @Override
    public LrcData parse(String content) throws LyricsParseException {
        if (TextUtils.isEmpty(content)) {
            throw new LyricsParseException("Content is empty");
        }
        
        LrcData lrcData = new LrcData();
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        
        // Current cue being collected. -1 means "we are between cues" — any text seen
        // there is file-level junk (cue numbers, headers) and gets ignored.
        long cueStartMs = -1L;
        StringBuilder cueText = new StringBuilder();
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            Matcher timing = TIMING_LINE.matcher(line);
            
            if (timing.lookingAt()) {
                // A new timing line always closes the previous cue.
                flushCue(lrcData, cueStartMs, cueText);
                cueStartMs = parseTime(timing, 1);
                continue;
            }
            
            if (cueStartMs < 0L) {
                // Outside a cue — skip the index line and any leading garbage.
                continue;
            }
            
            if (line.isEmpty()) {
                flushCue(lrcData, cueStartMs, cueText);
                cueStartMs = -1L;
                continue;
            }
            
            // Some files omit the blank line between cues. A bare number that is
            // immediately followed by a timing line is the next cue's index, not text.
            if (CUE_INDEX.matcher(line).matches() && isFollowedByTimingLine(lines, i + 1)) {
                flushCue(lrcData, cueStartMs, cueText);
                cueStartMs = -1L;
                continue;
            }
            
            String cleaned = cleanText(line);
            if (cleaned.isEmpty()) {
                continue;
            }
            
            if (cueText.length() > 0) {
                cueText.append('\n');
            }
            cueText.append(cleaned);
        }
        
        // File may end without a trailing blank line.
        flushCue(lrcData, cueStartMs, cueText);
        
        lrcData.sort();
        return lrcData;
    }
    
    @Override
    public boolean canParse(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        
        // The arrow is the one token every SRT file has and no LRC file uses.
        return TIMING_LINE.matcher(content).find();
    }
    
    /**
     * Stores the collected cue, if it has both a timestamp and some text.
     * Silently does nothing for empty cues — subtitle files often contain them.
     */
    private void flushCue(LrcData lrcData, long startMs, StringBuilder text) {
        if (startMs >= 0L && text.length() > 0) {
            lrcData.addEntry(new LrcEntry(startMs, text.toString()));
        }
        text.setLength(0);
    }
    
    /**
     * Returns true when the next non-empty line is a SubRip timing line.
     */
    private boolean isFollowedByTimingLine(String[] lines, int fromIndex) {
        for (int i = fromIndex; i < lines.length; i++) {
            String candidate = lines[i].trim();
            if (candidate.isEmpty()) {
                continue;
            }
            return TIMING_LINE.matcher(candidate).lookingAt();
        }
        return false;
    }
    
    /**
     * Parses one side of a timing line into milliseconds.
     *
     * @param matcher the matcher positioned on a {@link #TIMING_LINE} match
     * @param offset  the first group of the side to parse (1 for start, 5 for end)
     */
    private long parseTime(Matcher matcher, int offset) {
        try {
            String hours = matcher.group(offset);
            long minutes = Long.parseLong(matcher.group(offset + 1));
            long seconds = Long.parseLong(matcher.group(offset + 2));
            long millis = parseMillis(matcher.group(offset + 3));
            
            long totalHours = hours != null ? Long.parseLong(hours) : 0L;
            
            return (totalHours * 3600L * 1000L)
                    + (minutes * 60L * 1000L)
                    + (seconds * 1000L)
                    + millis;
        } catch (Exception e) {
            return 0L;
        }
    }
    
    /**
     * Turns the fractional part of a timestamp into milliseconds. SRT normally uses
     * exactly three digits, but we tolerate one or two as well.
     */
    private long parseMillis(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0L;
        }
        if (raw.length() >= 3) {
            return Long.parseLong(raw.substring(0, 3));
        }
        long value = Long.parseLong(raw);
        return raw.length() == 2 ? value * 10L : value * 100L;
    }
    
    /**
     * Removes markup and decodes the handful of HTML entities that show up in subtitles.
     * Literal {@code \N} line breaks (an ASS leftover) are turned into real newlines.
     */
    private String cleanText(String line) {
        String text = HTML_TAG.matcher(line).replaceAll("");
        text = ASS_OVERRIDE.matcher(text).replaceAll("");
        text = text.replace("\\N", "\n").replace("\\n", "\n");
        text = unescapeEntities(text);
        
        // Collapse the padding that markup removal tends to leave behind.
        StringBuilder builder = new StringBuilder(text.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                if (!lastWasSpace) {
                    builder.append(' ');
                }
                lastWasSpace = true;
            } else {
                builder.append(c);
                lastWasSpace = c == '\n';
            }
        }
        
        return builder.toString().trim();
    }
    
    /**
     * Decodes the basic HTML entities SubRip tools like to emit.
     */
    private String unescapeEntities(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
