package app.simple.felicity.decorations.highlight;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.Objects;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
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
 *     <li>{@link HighlightViewDelegate#MODE_FLAT} – filled pill background using the theme highlight color (default).</li>
 *     <li>{@link HighlightViewDelegate#MODE_OUTLINE} – stroke-only border around the view, no fill.</li>
 *     <li>{@link HighlightViewDelegate#MODE_BOTH} – filled background with an accent-colored stroke on top.</li>
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

    public static final int MODE_AUTO = -1;
    public static final int MODE_FLAT = HighlightViewDelegate.MODE_FLAT;
    public static final int MODE_OUTLINE = HighlightViewDelegate.MODE_OUTLINE;
    public static final int MODE_BOTH = HighlightViewDelegate.MODE_BOTH;

    private static final float DEFAULT_STROKE_DP = 0.5f;
    
    private final HighlightViewDelegate delegate = new HighlightViewDelegate(0f);

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
     * Last known fill/stroke/ripple colors so animations always start from the right place.
     */
    @ColorInt
    private int lastFillColor = Color.TRANSPARENT;
    @ColorInt
    private int lastStrokeColor = Color.TRANSPARENT;
    @ColorInt
    private int lastRippleStartColor = Color.TRANSPARENT;

    public HighlightTextView(@NonNull Context context) {
        super(context);
        delegate.setStrokeWidthPx(UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP));
        init(null);
    }

    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        delegate.setStrokeWidthPx(UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP));
        init(attrs);
    }

    public HighlightTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        delegate.setStrokeWidthPx(UnitUtils.INSTANCE.dpToPx(context, DEFAULT_STROKE_DP));
        init(attrs);
    }

    private void init(@Nullable AttributeSet attrs) {
        if (isInEditMode()) {
            return;
        }
        if (attrs != null) {
            android.content.res.TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HighlightTextView);
            try {
                delegate.setHighlightMode(a.getInt(R.styleable.HighlightTextView_highlightMode, MODE_FLAT));
                delegate.setStrokeWidthPx(a.getDimension(R.styleable.HighlightTextView_highlightStrokeWidth, delegate.getStrokeWidthPx()));
                delegate.setUseAccentColor(a.getBoolean(R.styleable.HighlightTextView_useAccentColor, false));
                if (!delegate.getUseAccentColor() && a.hasValue(R.styleable.HighlightTextView_highlightCustomColor)) {
                    delegate.setCustomColor(a.getColor(R.styleable.HighlightTextView_highlightCustomColor, Color.TRANSPARENT));
                    delegate.setUseCustomColor(true);
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
     * the current mode and active color. When {@code animate} is true the fill and stroke colors
     * cross-fade from their previous values to the new ones.
     */
    private void applyChipBackground(boolean animate) {
        float cornerRadius = delegate.getCornerRadius();

        if (backgroundDrawable == null) {
            backgroundDrawable = new MaterialShapeDrawable(
                    new ShapeAppearanceModel()
                            .toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                            .build());
            setBackground(backgroundDrawable);
        } else {
            backgroundDrawable.setShapeAppearanceModel(
                    backgroundDrawable.getShapeAppearanceModel()
                            .toBuilder()
                            .setAllCorners(CornerFamily.ROUNDED, cornerRadius)
                            .build());
        }
        
        int targetFill = delegate.resolveFillColor();
        int targetStroke = delegate.resolveStrokeColor();
        int mode = delegate.getHighlightMode();
        boolean needsStroke = mode == MODE_OUTLINE
                || mode == MODE_BOTH
                || (mode == MODE_AUTO && AccessibilityPreferences.INSTANCE.isHighlightStroke());

        if (animate) {
            animateBackgroundFill(lastFillColor, mode == MODE_OUTLINE ? Color.TRANSPARENT : targetFill);
            if (needsStroke) {
                animateBackgroundStroke(lastStrokeColor, targetStroke);
            } else {
                backgroundDrawable.setStroke(0, Color.TRANSPARENT);
            }
        } else {
            switch (mode) {
                case MODE_AUTO:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(targetFill));
                    if (AccessibilityPreferences.INSTANCE.isHighlightStroke()) {
                        backgroundDrawable.setStroke(delegate.getStrokeWidthPx(), targetStroke);
                    }
                    break;
                case MODE_OUTLINE:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(Color.TRANSPARENT));
                    backgroundDrawable.setStroke(delegate.getStrokeWidthPx(), targetStroke);
                    break;
                case MODE_BOTH:
                    backgroundDrawable.setFillColor(ColorStateList.valueOf(targetFill));
                    backgroundDrawable.setStroke(delegate.getStrokeWidthPx(), targetStroke);
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

    private void animateBackgroundFill(@ColorInt int from, @ColorInt int to) {
        // In outline mode the fill is always transparent — snap it instead of animating.
        if (to == Color.TRANSPARENT) {
            backgroundDrawable.setFillColor(ColorStateList.valueOf(Color.TRANSPARENT));
            return;
        }
        HighlightViewDelegate.animateColor(from, to, HighlightViewDelegate.COLOR_ANIM_DURATION,
                color -> backgroundDrawable.setFillColor(ColorStateList.valueOf(color)));
    }

    private void animateBackgroundStroke(@ColorInt int from, @ColorInt int to) {
        HighlightViewDelegate.animateColor(from, to, HighlightViewDelegate.COLOR_ANIM_DURATION,
                color -> backgroundDrawable.setStroke(delegate.getStrokeWidthPx(), color));
    }

    /**
     * Builds (or updates) the {@link FelicityRippleDrawable} foreground. When {@code animate}
     * is true the ripple's start color fades to the new value rather than jumping.
     */
    private void applyRippleForeground(boolean animate) {
        float cornerRadius = delegate.getCornerRadius();
        int rippleColor = delegate.getUseCustomColor()
                ? delegate.getCustomColor()
                : ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
        int targetStartColor = (delegate.getHighlightMode() == MODE_OUTLINE)
                ? Color.TRANSPARENT
                : delegate.resolveFillColor();

        if (foregroundRipple == null) {
            foregroundRipple = new FelicityRippleDrawable(rippleColor);
            foregroundRipple.setCornerRadius(cornerRadius);
            foregroundRipple.setStartColor(targetStartColor);
            setForeground(foregroundRipple);
        } else {
            foregroundRipple.setCornerRadius(cornerRadius);
            foregroundRipple.setRippleColor(rippleColor);
            if (animate && targetStartColor != Color.TRANSPARENT) {
                HighlightViewDelegate.animateColor(lastRippleStartColor, targetStartColor,
                        HighlightViewDelegate.COLOR_ANIM_DURATION,
                        color -> foregroundRipple.setStartColor(color));
            } else {
                foregroundRipple.setStartColor(targetStartColor);
            }
        }

        lastRippleStartColor = targetStartColor;
    }

    /**
     * Handles text color and drawable tint changes together. When accent mode is on,
     * both are set to white so they stay readable on the colored background.
     *
     * @param animate whether to smoothly transition between colors or snap immediately.
     */
    private void applyTextAndDrawableColors(boolean animate) {
        if (delegate.getUseAccentColor()) {
            if (animate) {
                HighlightViewDelegate.animateColor(getCurrentTextColor(), Color.WHITE,
                        HighlightViewDelegate.COLOR_ANIM_DURATION, color -> setTextColor(color));
                HighlightViewDelegate.animateColor(lastDrawableTintColor, Color.WHITE,
                        HighlightViewDelegate.COLOR_ANIM_DURATION,
                        color -> TextViewCompat.setCompoundDrawableTintList(
                                this, ColorStateList.valueOf(color)));
            } else {
                setTextColor(Color.WHITE);
                TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.WHITE));
            }
            lastDrawableTintColor = Color.WHITE;
        } else {
            super.onThemeChanged(ThemeManager.INSTANCE.getTheme(), animate);
            lastDrawableTintColor = ThemeManager.INSTANCE.getTheme().getIconTheme().getRegularIconColor();
        }
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        if (!delegate.getUseAccentColor()) {
            super.onThemeChanged(theme, animate);
        }
        if (!delegate.getUseCustomColor() || delegate.getUseAccentColor()) {
            applyChipBackground(animate);
            applyRippleForeground(animate);
        }
        if (delegate.getUseAccentColor()) {
            applyTextAndDrawableColors(animate);
        }
    }

    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        if (!delegate.getUseAccentColor()) {
            super.onAccentChanged(accent);
        }
        if (!delegate.getUseCustomColor() || delegate.getUseAccentColor()) {
            applyChipBackground(true);
            applyRippleForeground(true);
        }
        if (delegate.getUseAccentColor()) {
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
        } else if ((!delegate.getUseCustomColor() || delegate.getUseAccentColor())
                && Objects.equals(key, AppearancePreferences.ACCENT_COLOR)) {
            applyChipBackground(false);
            applyRippleForeground(false);
        }
    }
    
    @SuppressLint ("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        long animDuration = getResources().getInteger(R.integer.animation_duration);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                HighlightViewDelegate.animateTouchDown(this, animDuration);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                HighlightViewDelegate.animateTouchUp(this, animDuration);
                break;
        }
        return super.onTouchEvent(event);
    }
    
    /**
     * Sets the highlight display mode at runtime. The background and outline will animate
     * to the new look rather than snapping instantly.
     *
     * @param mode one of {@link #MODE_FLAT}, {@link #MODE_OUTLINE}, or {@link #MODE_BOTH}.
     */
    public void setHighlightMode(int mode) {
        delegate.setHighlightMode(mode);
        applyChipBackground(true);
        applyRippleForeground(true);
        if (delegate.getUseAccentColor()) {
            applyTextAndDrawableColors(true);
        }
    }
    
    /**
     * Pins the fill and stroke color to {@code color}, bypassing any theme or accent entirely.
     * Pass {@link Color#TRANSPARENT} to revert to theme-driven colors.
     *
     * @param color the ARGB color to use, or {@link Color#TRANSPARENT} to clear.
     */
    public void setCustomHighlightColor(@ColorInt int color) {
        delegate.applyCustomColor(color);
        applyChipBackground(true);
        applyRippleForeground(true);
    }
    
    /**
     * Switches the view into accent-color mode at runtime. Pass {@code false} to go back
     * to the normal theme-driven or custom-color behavior.
     *
     * @param useAccent {@code true} to use accent color for everything.
     */
    public void setUseAccentColor(boolean useAccent) {
        delegate.setUseAccentColor(useAccent);
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
