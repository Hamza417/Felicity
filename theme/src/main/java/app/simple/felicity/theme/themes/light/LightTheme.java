package app.simple.felicity.theme.themes.light;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class LightTheme extends Theme {

    public LightTheme() {
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#2E2E2E")  // Regular Switch Color
        ));

        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#121212"), // Header Text Color
                Color.parseColor("#2B2B2B"), // Primary Text Color
                Color.parseColor("#5A5A5A"), // Secondary Text Color
                Color.parseColor("#7A7A7A"), // Tertiary Text Color
                Color.parseColor("#9A9A9A")  // Quaternary Text Color
        ));

        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#ffffff"), // Background Color
                Color.parseColor("#F6F6F6"), // Highlight Color
                Color.parseColor("#F1F1F1"), // Selected Color
                Color.parseColor("#DDDDDD")  // Divider Color
        ));

        setIconTheme(new IconTheme(
                Color.parseColor("#2E2E2E"), // Regular Icon Color
                Color.parseColor("#B1B1B1"), // Secondary Icon Color
                Color.parseColor("#F6F6F6")  // Disabled Icon Color
        ));
    }
}
