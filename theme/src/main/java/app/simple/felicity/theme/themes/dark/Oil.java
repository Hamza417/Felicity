package app.simple.felicity.theme.themes.dark;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class Oil extends Theme {
    
    public Oil() {
        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#e6e6e6"), // Heading Text Color
                Color.parseColor("#f6f8f5"), // Primary Text Color
                Color.parseColor("#C8C8C8"), // Secondary Text Color
                Color.parseColor("#AAAAAA"), // Tertiary Text Color
                Color.parseColor("#9A9A9A")  // Quaternary Text Color
        ));
        
        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#1A1C1B"), // Background
                Color.parseColor("#2a332e"), // Highlight Background
                Color.parseColor("#232e28"), // Selected Background
                Color.parseColor("#304539"),  // Divider Background
                Color.parseColor("#1d2621")   // Spot Background
        ));
        
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#1d2621") // Switch Off Color
        ));
        
        setIconTheme(new IconTheme(
                Color.parseColor("#F8F8F8"), // Regular Icon Color
                Color.parseColor("#E8E8E8"), // Secondary Icon Color
                Color.parseColor("#2a332e")  // Disabled Icon Color (fallback)
        ));
    }
}