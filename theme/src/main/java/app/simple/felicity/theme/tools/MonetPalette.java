package app.simple.felicity.theme.tools;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.HashMap;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * Generates a full Material 3 tonal palette from a seed color extracted from a provided
 * {@link Bitmap}. Approximates the system_accent1, neutral, and neutral-variant palette
 * families used in Material You dynamic color.
 * <p>
 * Derivation strategy:
 * <ul>
 *   <li>Extract a seed color by estimating the dominant color via quantized histogram sampling.</li>
 *   <li>Convert to a CAM16-based HCT (Hue, Chroma, Tone) color space via LAB.</li>
 *   <li>Generate tones using the MD3 spec: tone values represent perceptual lightness (0–100).</li>
 *   <li>Apply chroma limits to prevent oversaturated or eye-straining colors.</li>
 *   <li>Neutral and neutral-variant palettes use the same hue but with strongly reduced chroma.</li>
 * </ul>
 *
 * @author Hamza417
 */
public class MonetPalette {
    
    /**
     * MD3 minimum chroma to keep some color presence in the primary palette.
     */
    private static final double MIN_CHROMA = 16.0;
    /**
     * MD3 maximum chroma to prevent oversaturation (MD3 spec caps at ~48).
     */
    private static final double MAX_CHROMA = 48.0;
    /**
     * Chroma used for the neutral palette (near-grey, hue-tinted).
     */
    private static final double NEUTRAL_CHROMA = 4.0;
    /**
     * Chroma used for the neutral-variant palette (slightly more colorful than neutral).
     */
    private static final double NEUTRAL_VARIANT_CHROMA = 8.0;
    
    @ColorInt
    private final int seedColor;
    
    @ColorInt
    private final int accent1_500; // MD3 tone 40 (primary, light theme)
    
    @ColorInt
    private final int accent1_300; // MD3 tone 80 (primary, dark theme)
    
    /**
     * Hue angle of the seed color in degrees (0–360).
     */
    private final double hue;
    /** Clamped chroma of the primary tonal palette. */
    private final double chroma;
    
    public MonetPalette(@NonNull Bitmap bitmap) {
        this.seedColor = extractSeed(bitmap);
        
        // Convert to HCT color space via LAB
        double[] hct = rgbToHct(seedColor);
        this.hue = hct[0];
        this.chroma = clamp(hct[1], MIN_CHROMA, MAX_CHROMA);
        
        // Generate MD3-compliant tones
        // Tone 40: medium contrast for primary surfaces (light-theme primary)
        // Tone 80: light, suitable for primary in dark theme
        this.accent1_500 = hctToRgb(hue, chroma, 40.0);
        this.accent1_300 = hctToRgb(hue, chroma * 0.8, 80.0); // Reduce chroma for lighter tone
    }
    
    /**
     * Estimate the dominant color using quantized histogram sampling.
     * Quantize RGB to 5 bits per channel and tally across a sampled grid.
     * Weights highly saturated pixels more heavily to find vibrant accents.
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
                
                // --- Fast Vibrancy Weighting ---
                // Calculate max and min RGB values to approximate lightness and saturation
                int cMax = Math.max(r, Math.max(g, b));
                int cMin = Math.min(r, Math.min(g, b));
                int delta = cMax - cMin;
                
                // Filter out severely dark colors or washed-out near-whites
                if (cMax < 30 || (cMax > 240 && delta < 20)) {
                    continue;
                }
                
                // Exponential weighting based on color purity (delta).
                // A higher delta means the color is further from grey.
                // We shift right by 6 as a fast division to keep numbers manageable.
                int weight = (delta * delta) >> 6;
                if (weight < 1) {
                    weight = 1; // Base weight for any valid pixel
                }
                
                // Quantize to 5 bits per channel
                int rq = r >> 3;
                int gq = g >> 3;
                int bq = b >> 3;
                int key = (rq << 10) | (gq << 5) | bq;
                
                Integer old = histogram.get(key);
                // Add the calculated vibrancy weight instead of just a flat +1
                int count = (old == null ? 0 : old) + weight;
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
     * Approximation of system_accent1_500 (MD3 tone 40 — medium contrast, primary in light theme).
     */
    @ColorInt
    public int getAccent1_500() {
        return accent1_500;
    }
    
    /**
     * Approximation of system_accent1_300 (MD3 tone 80 — primary in dark theme).
     */
    @ColorInt
    public int getAccent1_300() {
        return accent1_300;
    }
    
    /**
     * Returns a color from the primary tonal palette at the requested MD3 tone.
     * The palette is derived from the album art seed color with full chroma.
     *
     * @param tone perceptual lightness value in the range [0, 100]
     * @return an opaque ARGB color at the given tone
     */
    @ColorInt
    public int getPrimaryTone(double tone) {
        return hctToRgb(hue, chroma, tone);
    }
    
    /**
     * Returns a color from the neutral tonal palette at the requested MD3 tone.
     * Uses the same hue as the seed but with very low chroma (~4), producing near-grey
     * tones that are suitable for backgrounds, text, and icon colors.
     *
     * @param tone perceptual lightness value in the range [0, 100]
     * @return an opaque ARGB color at the given tone
     */
    @ColorInt
    public int getNeutralTone(double tone) {
        return hctToRgb(hue, NEUTRAL_CHROMA, tone);
    }
    
    /**
     * Returns a color from the neutral-variant tonal palette at the requested MD3 tone.
     * Uses slightly higher chroma than the neutral palette (~8), suited for surface variants,
     * outlines, dividers, and switch tracks.
     *
     * @param tone perceptual lightness value in the range [0, 100]
     * @return an opaque ARGB color at the given tone
     */
    @ColorInt
    public int getNeutralVariantTone(double tone) {
        return hctToRgb(hue, NEUTRAL_VARIANT_CHROMA, tone);
    }
}