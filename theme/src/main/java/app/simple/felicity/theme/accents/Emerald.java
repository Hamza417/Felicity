package app.simple.felicity.theme.accents;

import android.graphics.Color;

import app.simple.felicity.theme.models.Accent;

/**
 * A fresh, vibrant green accent inspired by the classic emerald gem.
 * Perfect for when you want your app to feel a little more alive and earthy.
 */
public class Emerald extends Accent {
    
    public static final String IDENTIFIER = "emerald";
    
    public Emerald() {
        super(Color.parseColor("#2ECC71"), Color.parseColor("#27AE60"), IDENTIFIER);
    }
}

