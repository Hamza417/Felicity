package app.simple.felicity.decorations.lrc.parser;

import app.simple.felicity.decorations.lrc.model.LrcData;

/**
 * Interface for lyrics parsers
 * Allows different formats (LRC, SRT, etc.) to be parsed into a common format
 */
public interface ILyricsParser {
    /**
     * Parse lyrics content into LrcData
     *
     * @param content The raw lyrics content as string
     * @return Parsed LrcData object
     * @throws LyricsParseException if parsing fails
     */
    LrcData parse(String content) throws LyricsParseException;
    
    /**
     * Check if this parser can handle the given content
     *
     * @param content The raw lyrics content
     * @return true if this parser can handle the content
     */
    boolean canParse(String content);
}
