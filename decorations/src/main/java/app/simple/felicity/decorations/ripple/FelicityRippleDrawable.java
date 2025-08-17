package app.simple.felicity.decorations.ripple;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A lightweight custom ripple drawable that creates a "sticky" slow ripple
 * while the view is pressed and a sudden expand + fade on release/cancel.
 * <p>
 * Usage (API 21+ recommended as foreground):
 * view.setForeground(new FelicityRippleDrawable(color));
 * or as background if foreground not desired.
 */
public class FelicityRippleDrawable extends Drawable {
    // Configuration
    private static final long PRESS_GROW_DURATION = 850L; // ms to reach mid radius
    // Removed fixed RELEASE_ANIMATION_DURATION; use adaptive duration instead
    private static final long MIN_RELEASE_DURATION = 360L; // when almost fully grown
    private static final long MAX_RELEASE_DURATION = 420L; // when released immediately
    private static final float PRESS_TARGET_FRACTION = 0.75f; // fraction of max it grows to while held
    private static final int PRESSED_ALPHA = 128; // out of 255
    private static final TimeInterpolator PRESS_INTERPOLATOR = new DecelerateInterpolator();
    private static final TimeInterpolator RELEASE_INTERPOLATOR = new DecelerateInterpolator();
    // Paint & color handling
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private int startColor = Color.parseColor("#808080"); // muted initial color (grey)
    private int endColor; // final ripple color user sets
    private int currentColor; // color currently being drawn (with applied pressed alpha/overallAlpha)
    // Animation state
    private float radius; // current radius

    private float hotspotX = -1f;
    private float hotspotY = -1f;
    private float maxRadius; // computed from bounds & hotspot
    private boolean pressed = false;
    private boolean running = false;
    private ValueAnimator pressAnimator;   // slow growth while pressed
    private ValueAnimator releaseAnimator; // fast expand + fade out
    // Drawable alpha
    private int overallAlpha = 128; // 0..255
    private float cornerRadius = 0f;
    private boolean clipDirty = true;
    
    public FelicityRippleDrawable(@ColorInt int color) {
        setRippleColor(color);
        paint.setStyle(Paint.Style.FILL);
        updateResolvedColor();
    }
    
    private static float lerp(float a, float b, @FloatRange (from = 0, to = 1) float t) {
        return a + (b - a) * t;
    }
    
    private static int blendARGB(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = Color.alpha(c1);
        int r1 = Color.red(c1);
        int g1 = Color.green(c1);
        int b1 = Color.blue(c1);
        int a2 = Color.alpha(c2);
        int r2 = Color.red(c2);
        int g2 = Color.green(c2);
        int b2 = Color.blue(c2);
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return Color.argb(a, r, g, b);
    }
    
    /**
     * Optional: adjust internal pressed alpha multiplier (0..255). Call before attaching.
     */
    public FelicityRippleDrawable setPressedAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
        return this;
    }
    
    public void setColor(@ColorInt int color) { // backward compatibility
        setRippleColor(color);
    }
    
    public void setRippleColor(@ColorInt int color) {
        this.endColor = color;
        updateResolvedColor();
        invalidateSelf();
    }
    
    public void setStartColor(@ColorInt int color) {
        this.startColor = color;
        updateResolvedColor();
        invalidateSelf();
    }
    
    private void updateResolvedColor() {
        // When idle (not pressed / not animating) we show nothing (radius 0) but prepare currentColor.
        int base = pressed ? endColor : startColor;
        currentColor = applyPressedMask(base);
        paint.setColor(currentColor);
    }
    
    private int applyPressedMask(int color) {
        int a = Color.alpha(color);
        float pressedFactor = (PRESSED_ALPHA / 255f) * (overallAlpha / 255f);
        int na = (int) (a * pressedFactor);
        return Color.argb(na, Color.red(color), Color.green(color), Color.blue(color));
    }
    
    /**
     * Hotspot center (typically MotionEvent coordinates relative to view)
     */
    public void setHotspot(float x, float y) {
        this.hotspotX = x;
        this.hotspotY = y;
        computeMaxRadius();
        invalidateSelf();
    }
    
    private void computeMaxRadius() {
        Rect b = getBounds();
        float cx = hotspotX >= 0 ? hotspotX : b.exactCenterX();
        float cy = hotspotY >= 0 ? hotspotY : b.exactCenterY();
        float dx = Math.max(cx - b.left, b.right - cx);
        float dy = Math.max(cy - b.top, b.bottom - cy);
        maxRadius = (float) Math.hypot(dx, dy);
        if (radius > maxRadius) {
            radius = maxRadius;
        }
    }
    
    /**
     * Set corner radius (in pixels) for clipping the ripple inside a rounded rectangle.
     */
    public void setCornerRadius(float cornerRadius) {
        if (this.cornerRadius != cornerRadius) {
            this.cornerRadius = cornerRadius;
            clipDirty = true;
            invalidateSelf();
        }
    }
    
    private void ensureClipPath() {
        if (!clipDirty) {
            return;
        }
        clipPath.reset();
        Rect b = getBounds();
        if (cornerRadius > 0f) {
            clipPath.addRoundRect(b.left, b.top, b.right, b.bottom, cornerRadius, cornerRadius, Path.Direction.CW);
        } else {
            clipPath.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
        }
        clipDirty = false;
    }
    
    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        computeMaxRadius();
        clipDirty = true;
    }
    
    @Override
    public void draw(@NonNull Canvas canvas) {
        if (currentColor == 0) {
            return;
        }
        if (radius <= 0f) {
            return;
        }
        ensureClipPath();
        int save = canvas.save();
        canvas.clipPath(clipPath);
        float cx = hotspotX >= 0 ? hotspotX : getBounds().exactCenterX();
        float cy = hotspotY >= 0 ? hotspotY : getBounds().exactCenterY();
        paint.setColor(currentColor); // ensure current
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.restoreToCount(save);
    }
    
    @Override
    public int getAlpha() {
        return overallAlpha;
    }
    
    @Override
    public void setAlpha(int alpha) {
        overallAlpha = alpha;
        updateResolvedColor();
        invalidateSelf();
    }
    
    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }
    
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
    
    @Override
    public boolean isStateful() {
        return true; // we react to pressed state
    }
    
    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean nowPressed = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_pressed) {
                nowPressed = true;
                break;
            }
        }
        if (nowPressed != pressed) {
            pressed = nowPressed;
            if (pressed) {
                startPress();
            } else {
                startRelease();
            }
            return true;
        }
        return super.onStateChange(stateSet);
    }
    
    private void cancelAnim(ValueAnimator animator) {
        if (animator != null) {
            animator.cancel();
        }
    }
    
    private void startPress() {
        cancelAnim(releaseAnimator);
        cancelAnim(pressAnimator);
        running = true;
        if (radius == 0f) {
            radius = 0f;
        }
        float target = maxRadius * PRESS_TARGET_FRACTION;
        pressAnimator = ValueAnimator.ofFloat(radius, target);
        pressAnimator.setDuration(PRESS_GROW_DURATION);
        pressAnimator.setInterpolator(PRESS_INTERPOLATOR);
        pressAnimator.addUpdateListener(a -> {
            radius = (float) a.getAnimatedValue();
            float frac = a.getAnimatedFraction();
            // Interpolate color from startColor to endColor during press growth
            int blended = blendARGB(startColor, endColor, frac);
            currentColor = applyPressedMask(blended);
            invalidateSelf();
        });
        pressAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!pressed) {
                    return;
                }
                currentColor = applyPressedMask(endColor); // ensure final color while held
                paint.setColor(currentColor);
                running = false;
                invalidateSelf();
            }
        });
        pressAnimator.start();
    }
    
    private void startRelease() {
        cancelAnim(pressAnimator);
        cancelAnim(releaseAnimator);
        // Ensure a minimally visible radius so very quick taps still show a ripple
        float minVisible = maxRadius * 0.12f; // 12% of max
        if (radius < minVisible) {
            radius = minVisible;
        }
        float startRadiusLocal = radius;
        final float endRadius = maxRadius;
        final int colorAtRelease = currentColor;
        final int startAlpha = Color.alpha(colorAtRelease);
        // Adaptive duration based on remaining distance to travel (sqrt for perceptual uniformity)
        float remaining = Math.max(0f, endRadius - startRadiusLocal);
        float remainingFraction = remaining / endRadius; // 0..1
        long adaptiveDuration = (long) (MIN_RELEASE_DURATION + (MAX_RELEASE_DURATION - MIN_RELEASE_DURATION) * Math.sqrt(remainingFraction));
        if (adaptiveDuration < MIN_RELEASE_DURATION) {
            adaptiveDuration = MIN_RELEASE_DURATION;
        }
        // We'll also adapt fade so early releases keep some time to finish color transition to endColor before fading out.
        final boolean reachedTargetColor = (startRadiusLocal >= endRadius * PRESS_TARGET_FRACTION * 0.98f); // margin
        releaseAnimator = ValueAnimator.ofFloat(0f, 1f);
        releaseAnimator.setDuration(adaptiveDuration);
        releaseAnimator.setInterpolator(RELEASE_INTERPOLATOR);
        running = true;
        releaseAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // Smooth expansion
            radius = lerp(startRadiusLocal, endRadius, t);
            // Two-phase color handling if we released early: first blend to endColor, then fade
            if (!reachedTargetColor) {
                float blendPortion = 0.35f; // first 35% used to complete color blend
                if (t < blendPortion) {
                    float bt = t / blendPortion; // 0..1
                    currentColor = blendARGB(colorAtRelease, applyPressedMask(endColor), bt);
                } else {
                    float fadeT = (t - blendPortion) / (1f - blendPortion); // 0..1 for fade
                    int baseBlend = endColor;
                    int r = Color.red(baseBlend);
                    int g = Color.green(baseBlend);
                    int b = Color.blue(baseBlend);
                    int fadeAlpha = (int) (startAlpha * (1f - fadeT));
                    currentColor = Color.argb(fadeAlpha, r, g, b);
                }
            } else {
                // Normal fade from current color
                int fadeAlpha = (int) (startAlpha * (1f - t));
                int baseBlend = endColor == 0 ? colorAtRelease : endColor;
                currentColor = Color.argb(fadeAlpha, Color.red(baseBlend), Color.green(baseBlend), Color.blue(baseBlend));
            }
            paint.setColor(currentColor);
            invalidateSelf();
        });
        releaseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                running = false;
                radius = 0f;
                // Reset color to muted start state for next press
                currentColor = applyPressedMask(startColor);
                paint.setColor(currentColor);
                invalidateSelf();
            }
        });
        releaseAnimator.start();
    }
    
    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
    }
    
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Convenience: manually trigger a release (e.g. parent intercepted)
     */
    public void forceRelease() {
        if (pressed) {
            pressed = false;
            startRelease();
        }
    }
}
