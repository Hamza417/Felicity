package app.simple.felicity.theme.themes.light;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class HighContrastLight extends Theme {
    
    public HighContrastLight() {
        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#000000"), // Heading Text Color
                Color.parseColor("#000000"), // Primary Text Color
                Color.parseColor("#000000"), // Secondary Text Color
                Color.parseColor("#000000"), // Tertiary Text Color
                Color.parseColor("#000000")  // Quaternary Text Color
        ));
        
        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#ffffff"), // Background
                Color.parseColor("#EEEEEE"), // Highlight Background
                Color.parseColor("#EEEEEE"), // Selected Background
                Color.parseColor("#000000"),  // Divider Background
                Color.parseColor("#000000")   // Spot Background
        ));
        
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#000000") // Switch Off Color
        ));
        
        setIconTheme(new IconTheme(
                Color.parseColor("#000000"), // Regular Icon Color
                Color.parseColor("#000000"), // Secondary Icon Color
                Color.parseColor("#EEEEEE")  // Disabled Icon Color (fallback)
        ));
    }
}