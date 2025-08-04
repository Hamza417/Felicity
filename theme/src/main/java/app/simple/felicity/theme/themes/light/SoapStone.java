package app.simple.felicity.theme.themes.light;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class SoapStone extends Theme {
    
    public SoapStone() {
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#F4F4F4") // Switch Off Color
        ));
        
        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#161813"), // Heading Text Color
                Color.parseColor("#1e201b"), // Primary Text Color
                Color.parseColor("#5A5A5A"), // Secondary Text Color
                Color.parseColor("#7A7A7A"), // Tertiary Text Color
                Color.parseColor("#9A9A9A")  // Quaternary Text Color
        ));
        
        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#fbfdf8"), // Background
                Color.parseColor("#F6F6F6"), // Highlight Background
                Color.parseColor("#F1F1F1"), // Selected Background
                Color.parseColor("#767873"),  // Divider Background
                Color.parseColor("#D4D4D4")   // Spot Background
        ));
        
        setIconTheme(new IconTheme(
                Color.parseColor("#2E2E2E"), // Regular Icon Color
                Color.parseColor("#B1B1B1"), // Secondary Icon Color
                Color.parseColor("#F6F6F6")  // Disabled Icon Color (fallback)
        ));
    }
}