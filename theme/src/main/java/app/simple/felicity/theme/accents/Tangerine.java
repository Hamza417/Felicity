package app.simple.felicity.theme.accents;

import android.graphics.Color;

import app.simple.felicity.theme.models.Accent;

/**
 * A warm, punchy orange accent that's somewhere between a tangerine and
 * a sunset. Great for when the default blue just isn't exciting enough.
 */
public class Tangerine extends Accent {
    
    public static final String IDENTIFIER = "tangerine";
    
    public Tangerine() {
        super(Color.parseColor("#E67E22"), Color.parseColor("#CA6F1E"), IDENTIFIER);
    }
}

