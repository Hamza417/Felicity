package app.simple.felicity.theme.accents;

import app.simple.felicity.theme.models.Accent;

public class Felicity extends Accent {
    public Felicity(int primaryAccentColor, int secondaryAccentColor) {
        super(primaryAccentColor, secondaryAccentColor);
    }

    public Felicity() {
        super(0xFF2980b9, 0xFF5499c7);
    }
}
