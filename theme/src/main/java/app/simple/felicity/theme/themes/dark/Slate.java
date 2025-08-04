package app.simple.felicity.theme.themes.dark;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class Slate extends Theme {
    
    public Slate() {
        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#F1F1F1"), // Heading Text Color
                Color.parseColor("#E4E4E4"), // Primary Text Color
                Color.parseColor("#C8C8C8"), // Secondary Text Color
                Color.parseColor("#AAAAAA"), // Tertiary Text Color
                Color.parseColor("#9A9A9A")  // Quaternary Text Color
        ));
        
        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#20272e"), // Background
                Color.parseColor("#223343"), // Highlight Background
                Color.parseColor("#273f58"), // Selected Background
                Color.parseColor("#666666"),  // Divider Background
                Color.parseColor("#1A1A1A")   // Spot Background
        ));
        
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#314152") // Switch Off Color
        ));
        
        setIconTheme(new IconTheme(
                Color.parseColor("#F8F8F8"), // Regular Icon Color
                Color.parseColor("#E8E8E8"), // Secondary Icon Color
                Color.parseColor("#223343")  // Disabled Icon Color (fallback)
        ));
    }
}