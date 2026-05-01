package app.simple.felicity.theme.accents;

import android.graphics.Color;

import app.simple.felicity.theme.models.Accent;

/**
 * A soft rose pink accent — elegant, modern, and just edgy enough to stand
 * out without being in-your-face about it.
 */
public class Rose extends Accent {
    
    public static final String IDENTIFIER = "rose";
    
    public Rose() {
        super(Color.parseColor("#EC407A"), Color.parseColor("#C2185B"), IDENTIFIER);
    }
}

