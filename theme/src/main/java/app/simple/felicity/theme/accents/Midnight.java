package app.simple.felicity.theme.accents;

import android.graphics.Color;

import app.simple.felicity.theme.models.Accent;

/**
 * A deep midnight teal accent that feels like staring into a very stylish
 * ocean at 2 AM. Cool, moody, and surprisingly readable.
 */
public class Midnight extends Accent {
    
    public static final String IDENTIFIER = "midnight";
    
    public Midnight() {
        super(Color.parseColor("#1ABC9C"), Color.parseColor("#148F77"), IDENTIFIER);
    }
}

