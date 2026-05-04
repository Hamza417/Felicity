package app.simple.felicity.decorations.highlight;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.ripple.FelicityRippleDrawable;
import app.simple.felicity.decorations.typeface.TypeFaceTextView;
import app.simple.felicity.preferences.AccessibilityPreferences;
import app.simple.felicity.preferences.AppearancePreferences;
import app.simple.felicity.shared.utils.UnitUtils;
import app.simple.felicity.shared.utils.ViewUtils;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.models.Theme;

/**
 * A button-like {@link TypeFaceTextView} that renders a pill-shaped highlight with a
 * {@link FelicityRippleDrawable} ripple on click. Three visual modes are supported and can
 * be selected via the {@code highlightMode} XML attribute or
 * {@link #setHighlightMode(int)} at runtime:
 * <ul>
 *     <li>{@link #MODE_FLAT} – filled pill background using the theme highlight color (default).</li>
 *     <li>{@link #MODE_OUTLINE} – stroke-only border around the view, no fill.</li>
 *     <li>{@link #MODE_BOTH} – filled background with an accent-colored stroke on top.</li>
 * </ul>
 *
 * <p>The optional {@code highlightCustomColor} XML attribute pins the fill and stroke color to
 * an explicit value that is never overridden by theme or accent changes. Omit the attribute to
 * use the active theme colors. The custom color can also be changed at runtime via
 * {@link #setCustomHighlightColor(int)}; passing {@link Color#TRANSPARENT} reverts back to
 * theme-driven colors.</p>
 *
 * <p>Use wherever an image button is not appropriate but a tappable, visually distinct
 * label-button is needed.</p>
 *
 * @author Hamza417
 */
public class HighlightTextView extends TypeFaceTextView {
    
    /**
     * Automatically selects the display mode based on the current accessibility settings.
     */
    public static final int MODE_AUTO = -1;
    /**
     * Filled pill background using the theme highlight color. This is the default mode.
     */
    public static final int MODE_FLAT = 0;
    
    /**
     * Stroke-only border; no fill. The stroke color follows the accent (or custom color).
     */
    public static final int MODE_OUTLINE = 1;
    
    /**
     * Filled pill background plus an accent-colored (or custom-color) stroke.
     */
    public static final int MODE_BOTH = 2;
    
    private static final float DEFAULT_STROKE_DP = 0.5f;
    private static final long COLOR_ANIM_DURATION = 400L;

    private int highlightMode = MODE_FLAT;
    private boolean useCustomColor = false;
    /**
     * When true the accent color drives both fill and stroke, ignoring any custom color.
     * Text and drawable tints will also be forced to white so they remain readable on the
     * colored background.
     */
    private boolean useAccentColor = false;
    @ColorInt
    private int customColor = Color.TRANSPARENT;
    private float strokeWidth;
    
    /**
     * Tracks the last drawable tint color so we can smoothly animate away from it
     * rather than jumping from nothing.
     */
    @ColorInt
    private int lastDrawableTintColor = Color.GRAY;
    
    /**
     * Persistent background and foreground drawables — we mutate them in place rather than
     * replacing them on every update so we can animate their colors smoothly.
     */
    private MaterialShapeDrawable backgroundDrawable;
    private FelicityRippleDrawable foregroundRipple;
    
    /**
     * Last known fill/stroke colors so animations always start from the right place.
     */
    @ColorInt
    private int lastFillColor = Color.TRANSPARENT;
    @ColorInt
    private int lastStrokeColor = Color.TRANSPARENT;
    @ColorInt
    private int lastRippleStartColor = Color.TRANSPARENT;
    
    public HighlightTextView(@NonNull Context context) {
        super(context);
        strokeWidth = UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP);
        init(null);
    }
    
    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        strokeWidth = UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP);
        init(attrs);
    }
    
    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        strokeWidth = UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP);
        init(attrs);
    }
    
    private void init(@Nullable AttributeSet attrs) {
        if (isInEditMode()) {
            return;
        }
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HighlightTextView);
            try {
                highlightMode = a.getInt(R.styleable.HighlightTextView_highlightMode, MODE_FLAT);
                strokeWidth = a.getDimension(R.styleable.HighlightTextView_highlightStrokeWidth, strokeWidth);
                useAccentColor = a.getBoolean(R.styleable.HighlightTextView_useAccentColor, false);
                if (!useAccentColor && a.hasValue(R.styleable.HighlightTextView_highlightCustomColor)) {
                    customColor = a.getColor(R.styleable.HighlightTextView_highlightCustomColor, Color.TRANSPARENT);
                    useCustomColor = true;
                }
            } finally {
                a.recycle();
            }
        }
        applyChipBackground(false);
        applyRippleForeground(false);
        applyTextAndDrawableColors(false);
        setClickable(true);
        setFocusable(true);
    }
    
    /**
     * Builds (or updates) the pill-shaped {@link MaterialShapeDrawable} background according to
     * {@link #highlightMode} and the active color. When {@code animate} is true the fill and
     * stroke colors cross-fade from their previous values to the new ones.
     */
    private void applyChipBackground(boolean animate) {
        float cornerRadius = getGlobalRoundedRadius();
        
        if (backgroundDrawable == null) {
            backgroundDrawable = new MaterialShapeDrawable(
                    new ShapeAppearanceModel()
                            .toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                            .build());
            setBackground(backgroundDrawable);
        } else {
            // Keep the corner radius in sync with global setting changes.
            backgroundDrawable.setShapeAppearanceModel(
                    backgroundDrawable.getShapeAppearanceModel()
                            .toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                            .build());
        }
        
        int targetFill = resolveFillColor();
        int targetStroke = resolveStrokeColor();
        boolean needsStroke = highlightMode == MODE_OUTLINE
                || highlightMode == MODE_BOTH
                || (highlightMode == MODE_AUTO && AccessibilityPreferences.INSTANCE.isHighlightStroke());
        
        if (animate) {
            animateBackgroundFill(lastFillColor, highlightMode == MODE_OUTLINE ? Color.TRANSPARENT : targetFill);
            if (needsStroke) {
                animateBackgroundStroke(lastStrokeColor, targetStroke);
            } else {
                backgroundDrawable.setStroke(0, Color.TRANSPARENT);
            }
        } else {
            switch (highlightMode) {
                case MODE_AUTO:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(targetFill));
                    if (AccessibilityPreferences.INSTANCE.isHighlightStroke()) {
                        backgroundDrawable.setStroke(strokeWidth, targetStroke);
                    }
                    break;
                case MODE_OUTLINE:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(Color.TRANSPARENT));
                    backgroundDrawable.setStroke(strokeWidth, targetStroke);
                    break;
                case MODE_BOTH:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(targetFill));
                    backgroundDrawable.setStroke(strokeWidth, targetStroke);
                    break;
                case MODE_FLAT:
                default:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(targetFill));
                    backgroundDrawable.setStroke(0, Color.TRANSPARENT);
                    break;
            }
        }
        
        lastFillColor = targetFill;
        lastStrokeColor = targetStroke;
    }
    
    /**
     * Animates the background drawable's fill from {@code from} to {@code to}.
     */
    private void animateBackgroundFill(@ColorInt int from, @ColorInt int to) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(COLOR_ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(a ->
                backgroundDrawable.setFillColor(ColorStateList.valueOf((int) a.getAnimatedValue())));
        anim.start();
    }
    
    /**
     * Animates the background drawable's stroke from {@code from} to {@code to}.
     */
    private void animateBackgroundStroke(@ColorInt int from, @ColorInt int to) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(COLOR_ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(a ->
                backgroundDrawable.setStroke(strokeWidth, (int) a.getAnimatedValue()));
        anim.start();
    }
    
    /**
     * Builds (or updates) the {@link FelicityRippleDrawable} foreground. When {@code animate}
     * is true the ripple's background (start) color fades to the new value so there is no
     * jarring jump when the theme or accent changes.
     */
    private void applyRippleForeground(boolean animate) {
        float cornerRadius = getGlobalRoundedRadius();
        int rippleColor = useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        int targetStartColor = (highlightMode == MODE_OUTLINE) ? Color.TRANSPARENT : resolveFillColor();
        
        if (foregroundRipple == null) {
            foregroundRipple = new FelicityRippleDrawable(rippleColor);
            foregroundRipple.setCornerRadius(cornerRadius);
            foregroundRipple.setStartColor(targetStartColor);
            setForeground(foregroundRipple);
        } else {
            foregroundRipple.setCornerRadius(cornerRadius);
            foregroundRipple.setRippleColor(rippleColor);
            if (animate) {
                animateRippleStartColor(lastRippleStartColor, targetStartColor);
            } else {
                foregroundRipple.setStartColor(targetStartColor);
            }
        }
        
        lastRippleStartColor = targetStartColor;
    }
    
    /**
     * Smoothly fades the ripple's background (start) color between two values.
     */
    private void animateRippleStartColor(@ColorInt int from, @ColorInt int to) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(COLOR_ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(a -> foregroundRipple.setStartColor((int) a.getAnimatedValue()));
        anim.start();
    }
    
    /**
     * Handles text color and drawable tint changes together. When accent mode is on,
     * both are set to white so they stay readable on the colored background. When it's
     * off, the parent's theme-aware colors take over again.
     *
     * @param animate whether to smoothly transition between colors or snap immediately.
     */
    private void applyTextAndDrawableColors(boolean animate) {
        if (useAccentColor) {
            if (animate) {
                animateTextColor(getCurrentTextColor(), Color.WHITE);
                animateDrawableColor(lastDrawableTintColor, Color.WHITE);
            } else {
                setTextColor(Color.WHITE);
                TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.WHITE));
            }
            lastDrawableTintColor = Color.WHITE;
        } else {
            // Let the parent restore the colors it owns based on the XML style attributes.
            super.onThemeChanged(ThemeManager.INSTANCE.getTheme(), animate);
            lastDrawableTintColor = resolveNormalDrawableColor();
        }
    }
    
    /**
     * Smoothly transitions the text color from one value to another using a value animator.
     */
    private void animateTextColor(@ColorInt int from, @ColorInt int to) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(COLOR_ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(animation -> setTextColor((int) animation.getAnimatedValue()));
        anim.start();
    }
    
    /**
     * Smoothly transitions the compound drawable tint from one color to another.
     */
    private void animateDrawableColor(@ColorInt int from, @ColorInt int to) {
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, to);
        anim.setDuration(COLOR_ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(animation ->
                TextViewCompat.setCompoundDrawableTintList(
                        this, ColorStateList.valueOf((int) animation.getAnimatedValue())));
        anim.start();
    }
    
    /**
     * Figures out what the drawable tint color would be under normal (non-accent) conditions
     * so we have a correct starting point when animating back from white.
     */
    @ColorInt
    private int resolveNormalDrawableColor() {
        return ThemeManager.INSTANCE.getTheme().getIconTheme().getRegularIconColor();
    }
    
    /**
     * Returns the fill color based on priority:
     * useAccentColor wins first (the view really wants to stand out),
     * then a custom color if one was pinned, and finally the regular theme highlight color.
     */
    @ColorInt
    private int resolveFillColor() {
        if (useAccentColor) {
            return ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        }
        return useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor();
    }
    
    /**
     * Returns the stroke color using the same priority as fill — accent first,
     * then custom color, then the regular accent from the theme.
     */
    @ColorInt
    private int resolveStrokeColor() {
        if (useAccentColor) {
            return ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        }
        return useCustomColor
                ? customColor
                : ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
    }
    
    private float getGlobalRoundedRadius() {
        return AppearancePreferences.INSTANCE.getCornerRadius();
    }
    
    /**
     * Sets the highlight display mode at runtime. The background and outline will animate
     * to the new look rather than snapping instantly.
     *
     * @param mode one of {@link #MODE_FLAT}, {@link #MODE_OUTLINE}, or {@link #MODE_BOTH}.
     */
    public void setHighlightMode(int mode) {
        this.highlightMode = mode;
        applyChipBackground(true);
        applyRippleForeground(true);
        if (useAccentColor) {
            applyTextAndDrawableColors(true);
        }
    }
    
    /**
     * Pins the fill and stroke color to {@code color}, bypassing any theme or accent entirely.
     * The color change is animated for a smooth transition.
     * Pass {@link Color#TRANSPARENT} to revert to theme-driven colors.
     *
     * @param color the ARGB color to use, or {@link Color#TRANSPARENT} to clear.
     */
    public void setCustomHighlightColor(@ColorInt int color) {
        if (color == Color.TRANSPARENT) {
            useCustomColor = false;
            customColor = Color.TRANSPARENT;
        } else {
            useCustomColor = true;
            customColor = color;
        }
        applyChipBackground(true);
        applyRippleForeground(true);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        // When accent mode is on the parent would override our white text/drawable with its own
        // theme color, so we handle the color update ourselves instead.
        if (!useAccentColor) {
            super.onThemeChanged(theme, animate);
        }
        if (!useCustomColor || useAccentColor) {
            applyChipBackground(animate);
            applyRippleForeground(animate);
        }
        if (useAccentColor) {
            applyTextAndDrawableColors(animate);
        }
    }

    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        if (!useAccentColor) {
            super.onAccentChanged(accent);
        }
        if (!useCustomColor || useAccentColor) {
            applyChipBackground(true);
            applyRippleForeground(true);
        }
        if (useAccentColor) {
            // The accent color itself changed, so re-apply the background but keep text white.
            applyTextAndDrawableColors(true);
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(@Nullable SharedPreferences sharedPreferences,
            @Nullable String key) {
        super.onSharedPreferenceChanged(sharedPreferences, key);
        if (Objects.equals(key, AppearancePreferences.APP_CORNER_RADIUS)) {
            applyChipBackground(false);
            applyRippleForeground(false);
        } else if ((!useCustomColor || useAccentColor) && Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            applyChipBackground(false);
            applyRippleForeground(false);
        }
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode() && isClickable()) {
                    animate()
                            .scaleX(0.9F)
                            .scaleY(0.9F)
                            .alpha(0.7F)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .setDuration(getResources().getInteger(R.integer.animation_duration))
                            .start();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (AccessibilityPreferences.INSTANCE.isHighlightMode() && isClickable()) {
                    animate()
                            .scaleX(1F)
                            .scaleY(1F)
                            .alpha(1F)
                            .setStartDelay(50)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .setDuration(getResources().getInteger(R.integer.animation_duration))
                            .start();
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }
    
    /**
     * Switches the view into accent-color mode at runtime — the background, text, and
     * drawable will all animate smoothly to reflect the change. Pass {@code false} to go
     * back to the normal theme-driven or custom-color behavior.
     *
     * @param useAccent {@code true} to use accent color for everything.
     */
    public void setUseAccentColor(boolean useAccent) {
        this.useAccentColor = useAccent;
        applyChipBackground(true);
        applyRippleForeground(true);
        applyTextAndDrawableColors(true);
    }
    
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        ViewUtils.INSTANCE.triggerHover(this, event);
        return super.onGenericMotionEvent(event);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearAnimation();
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
    }
}
