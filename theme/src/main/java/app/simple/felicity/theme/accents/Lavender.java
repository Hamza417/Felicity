package app.simple.felicity.theme.accents;

import android.graphics.Color;

import app.simple.felicity.theme.models.Accent;

/**
 * A calm lavender-purple accent that carries a hint of mystery and creativity.
 * Think twilight skies and cozy late-night listening sessions.
 */
public class Lavender extends Accent {
    
    public static final String IDENTIFIER = "lavender";
    
    public Lavender() {
        super(Color.parseColor("#9B59B6"), Color.parseColor("#7D3C98"), IDENTIFIER);
    }
}

