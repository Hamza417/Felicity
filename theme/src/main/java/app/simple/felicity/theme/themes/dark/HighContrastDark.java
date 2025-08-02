package app.simple.felicity.theme.themes.dark;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class HighContrastDark extends Theme {
    
    public HighContrastDark() {
        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#ffffff"), // Heading Text Color
                Color.parseColor("#ffffff"), // Primary Text Color
                Color.parseColor("#ffffff"), // Secondary Text Color
                Color.parseColor("#ffffff"), // Tertiary Text Color
                Color.parseColor("#ffffff")  // Quaternary Text Color
        ));
        
        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#000000"), // Background
                Color.parseColor("#404040"), // Highlight Background
                Color.parseColor("#242424"), // Selected Background
                Color.parseColor("#ffffff")  // Divider Background
        ));
        
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#252525") // Switch Off Color
        ));
        
        setIconTheme(new IconTheme(
                Color.parseColor("#ffffff"), // Regular Icon Color
                Color.parseColor("#E8E8E8"), // Secondary Icon Color
                Color.parseColor("#404040")  // Disabled Icon Color (fallback)
        ));
    }
}