package app.simple.felicity.theme.tools;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.HashMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * Generates Material You-inspired accent colors from a seed extracted
 * from a provided Bitmap. Approximates system_accent1 family tones.
 * <p>
 * Derivation strategy:
 * - Extract a seed color by estimating the dominant color via quantized sampling.
 * - Convert to CAM16-based HCT (Hue, Chroma, Tone) color space.
 * - Generate tones using MD3 spec: tone values represent perceptual lightness (0-100).
 * - Apply chroma limits to prevent oversaturated/eye-straining colors.
 */
public class MonetPalette {
    
    // MD3 chroma constraints for pleasant, eye-friendly colors
    private static final double MIN_CHROMA = 16.0; // Keep some color presence
    private static final double MAX_CHROMA = 48.0; // Prevent oversaturation (MD3 uses ~48 max)
    
    @ColorInt
    private final int seedColor;
    
    @ColorInt
    private final int accent1_500; // MD3 tone 40 (medium)
    
    @ColorInt
    private final int accent1_300; // MD3 tone 80 (light)
    
    public MonetPalette(@NonNull Bitmap bitmap) {
        this.seedColor = extractSeed(bitmap);
        
        // Convert to HCT color space via LAB
        double[] hct = rgbToHct(seedColor);
        double hue = hct[0];
        double chroma = clamp(hct[1], MIN_CHROMA, MAX_CHROMA);
        
        // Generate MD3-compliant tones
        // Tone 40: medium contrast for primary surfaces
        // Tone 80: light, suitable for backgrounds in light theme
        this.accent1_500 = hctToRgb(hue, chroma, 40.0);
        this.accent1_300 = hctToRgb(hue, chroma * 0.8, 80.0); // Reduce chroma for lighter tone
    }
    
    /**
     * Estimate the dominant color using quantized histogram sampling.
     * Quantize RGB to 5 bits per channel and tally across a sampled grid.
     */
    @ColorInt
    private static int extractSeed(@NonNull Bitmap bitmap) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return Color.GRAY;
        }
        
        final int maxSample = 64; // target grid size on the long edge
        final int step = Math.max(1, Math.max(width, height) / maxSample);
        
        HashMap <Integer, Integer> histogram = new HashMap <>();
        int bestKey = 0;
        int bestCount = 0;
        
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int c = bitmap.getPixel(x, y);
                int a = Color.alpha(c);
                if (a < 32) {
                    continue; // skip nearly transparent
                }
                int r = Color.red(c);
                int g = Color.green(c);
                int b = Color.blue(c);
                // Quantize to 5 bits per channel
                int rq = r >> 3;
                int gq = g >> 3;
                int bq = b >> 3;
                int key = (rq << 10) | (gq << 5) | bq;
                Integer old = histogram.get(key);
                int count = (old == null ? 0 : old) + 1;
                histogram.put(key, count);
                if (count > bestCount) {
                    bestCount = count;
                    bestKey = key;
                }
            }
        }
        
        if (bestCount == 0) {
            return Color.GRAY;
        }
        
        // Dequantize back to 8-bit, use bin center
        int rq = (bestKey >> 10) & 0x1F;
        int gq = (bestKey >> 5) & 0x1F;
        int bq = bestKey & 0x1F;
        int r = (rq * 255) / 31;
        int g = (gq * 255) / 31;
        int b = (bq * 255) / 31;
        return opaque(Color.rgb(r, g, b));
    }
    
    /**
     * Convert RGB color to HCT (Hue, Chroma, Tone).
     * Uses LAB color space as intermediate for perceptually uniform tone calculation.
     */
    private static double[] rgbToHct(@ColorInt int color) {
        double[] lab = new double[3];
        ColorUtils.colorToLAB(color, lab);
        
        double L = lab[0]; // L* (lightness): 0-100
        double a = lab[1]; // a*: green-red axis
        double b = lab[2]; // b*: blue-yellow axis
        
        // Calculate chroma (colorfulness)
        double chroma = Math.sqrt(a * a + b * b);
        
        // Calculate hue angle in degrees
        double hue = Math.toDegrees(Math.atan2(b, a));
        if (hue < 0) {
            hue += 360.0;
        }
        
        // Tone is essentially L* in LAB space (0-100)
        return new double[] {hue, chroma, L};
    }
    
    /**
     * Convert HCT back to RGB.
     * Uses target tone and chroma to generate perceptually uniform colors.
     */
    @ColorInt
    private static int hctToRgb(double hue, double chroma, double tone) {
        // Clamp tone to valid range
        tone = clamp(tone, 0.0, 100.0);
        chroma = Math.max(0.0, chroma);
        
        // Convert to LAB coordinates
        double L = tone;
        double hueRad = Math.toRadians(hue);
        double a = chroma * Math.cos(hueRad);
        double b = chroma * Math.sin(hueRad);
        
        // Convert LAB to RGB
        int color = ColorUtils.LABToColor(L, a, b);
        
        // Ensure result is valid and opaque
        return opaque(color);
    }
    
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
    
    // --- internals ---
    
    private static int opaque(@ColorInt int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }
    
    /**
     * @return the extracted seed color used to build tones
     */
    @ColorInt
    public int getSeedColor() {
        return seedColor;
    }
    
    /**
     * Approximation of system_accent1_500 (MD3 tone 40 - medium contrast)
     */
    @ColorInt
    public int getAccent1_500() {
        return accent1_500;
    }
    
    /**
     * Approximation of system_accent1_300 (MD3 tone 80 - light)
     */
    @ColorInt
    public int getAccent1_300() {
        return accent1_300;
    }
}
