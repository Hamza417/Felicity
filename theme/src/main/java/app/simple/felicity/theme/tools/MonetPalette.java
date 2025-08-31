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
 * - Convert to HSL and set lightness to approximate Material You tones:
 * 500 ≈ tone 60, 300 ≈ tone 80 (mapped here to HSL L 0.60f and 0.80f).
 * - Clamp lightness to avoid too dark/light results.
 */
public class MonetPalette {
    
    private static final float MIN_L = 0.28f; // Prevent too dark
    private static final float MAX_L = 0.68f; // Prevent too light
    private static final float MIN_S = 0.25f; // Keep some saturation
    private static final float MAX_S = 0.60f; // Prevent oversaturation
    
    @ColorInt
    private final int seedColor;
    
    @ColorInt
    private final int accent1_500; // ~tone60
    
    @ColorInt
    private final int accent1_300; // ~tone80
    
    public MonetPalette(@NonNull Bitmap bitmap) {
        this.seedColor = extractSeed(bitmap);
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(seedColor, hsl);
        // Normalize saturation
        hsl[1] = clamp(hsl[1], MIN_S, MAX_S);
        
        // Derive tones by setting lightness, with clamping
        this.accent1_500 = tone(hsl, 0.60f);
        this.accent1_300 = tone(hsl, 0.80f);
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
    
    private static int tone(float[] baseHsl, float targetL) {
        float[] hsl = new float[] {baseHsl[0], baseHsl[1], clamp(targetL, MIN_L, MAX_L)};
        return ColorUtils.HSLToColor(hsl);
    }
    
    private static float clamp(float v, float min, float max) {
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
     * Approximation of system_accent1_500 (mid tone ~60)
     */
    @ColorInt
    public int getAccent1_500() {
        return accent1_500;
    }
    
    /**
     * Approximation of system_accent1_300 (lighter tone ~80)
     */
    @ColorInt
    public int getAccent1_300() {
        return accent1_300;
    }
}
