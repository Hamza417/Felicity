package app.simple.felicity;

import org.junit.Test;

import androidx.core.graphics.ColorUtils;

public class ColorTest {
    @Test
    public void testColors() {
        double tone = 6.0;
        double chroma = 4.0;
        double hue = 120.0; // Greenish
        @SuppressWarnings ("UnnecessaryLocalVariable")
        double L = tone;
        double hueRad = Math.toRadians(hue);
        double a = chroma * Math.cos(hueRad);
        double b = chroma * Math.sin(hueRad);
        int color = ColorUtils.LABToColor(L, a, b);
        System.out.printf("L=%.1f, chroma=%.1f, hue=%.1f -> R=%d G=%d B=%d\n", tone, chroma, hue, (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF);
    }
}