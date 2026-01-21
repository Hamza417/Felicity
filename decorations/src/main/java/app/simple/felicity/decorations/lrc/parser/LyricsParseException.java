package app.simple.felicity.decorations.lrc.parser;

/**
 * Exception thrown when lyrics parsing fails
 */
public class LyricsParseException extends Exception {
    public LyricsParseException(String message) {
        super(message);
    }
    
    public LyricsParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
