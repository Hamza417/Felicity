package app.simple.felicity.theme.accents;

import android.annotation.SuppressLint;

import app.simple.felicity.theme.data.MaterialYou;
import app.simple.felicity.theme.models.Accent;

public class MaterialYouAccent extends Accent {
    @SuppressLint ("InlinedApi")
    public MaterialYouAccent() {
        super(MaterialYou.INSTANCE.getPrimaryAccentColor(),
                MaterialYou.INSTANCE.getSecondaryAccentColor());
    }
}
