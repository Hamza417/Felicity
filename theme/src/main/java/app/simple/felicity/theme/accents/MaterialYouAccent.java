package app.simple.felicity.theme.accents;

import androidx.annotation.RequiresApi;
import app.simple.felicity.theme.data.MaterialYou;
import app.simple.felicity.theme.models.Accent;

@RequiresApi (api = 31)
public class MaterialYouAccent extends Accent {
    
    public static final String IDENTIFIER = "material_you";
    
    public MaterialYouAccent() {
        super(MaterialYou.INSTANCE.getPrimaryAccentColor(),
                MaterialYou.INSTANCE.getSecondaryAccentColor(),
                IDENTIFIER);
    }
}
