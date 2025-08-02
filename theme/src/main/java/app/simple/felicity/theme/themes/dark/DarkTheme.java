package app.simple.felicity.theme.themes.dark;

import android.graphics.Color;

import app.simple.felicity.theme.models.IconTheme;
import app.simple.felicity.theme.models.SwitchTheme;
import app.simple.felicity.theme.models.TextViewTheme;
import app.simple.felicity.theme.models.ViewGroupTheme;
import app.simple.felicity.theme.themes.Theme;

public class DarkTheme extends Theme {

    public DarkTheme() {
        setSwitchTheme(new SwitchTheme(
                Color.parseColor("#252525")  // Regular Switch Color
        ));

        setTextViewTheme(new TextViewTheme(
                Color.parseColor("#F1F1F1"), // Header Text Color
                Color.parseColor("#E4E4E4"), // Primary Text Color
                Color.parseColor("#C8C8C8"), // Secondary Text Color
                Color.parseColor("#AAAAAA"), // Tertiary Text Color
                Color.parseColor("#9A9A9A")  // Quaternary Text Color
        ));

        setViewGroupTheme(new ViewGroupTheme(
                Color.parseColor("#171717"), // Background Color
                Color.parseColor("#404040"), // Highlight Color
                Color.parseColor("#242424"), // Selected Color
                Color.parseColor("#666666")  // Divider Color
        ));

        setIconTheme(new IconTheme(
                Color.parseColor("#F8F8F8"), // Regular Icon Color
                Color.parseColor("#E8E8E8"), // Secondary Icon Color
                Color.parseColor("#F6F6F6")  // Disabled Icon Color - TODO
        ));
    }
}
