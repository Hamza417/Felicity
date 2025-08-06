package app.simple.felicity.decorations.circular;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import app.simple.felicity.core.utils.ColorUtils;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decoration.R;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;
import app.simple.felicity.theme.themes.Theme;

public class CircularImageImageButton extends AppCompatImageButton implements ThemeChangedListener {
    
    private ShapeDrawable backgroundDrawable;
    private RippleDrawable rippleDrawable;
    private ValueAnimator valueAnimator;
    private final byte BACKGROUND_REGULAR = 0;
    private final byte BACKGROUND_SECONDARY = 1;
    private final byte BACKGROUND_ACCENT = 2;
    private final byte BACKGROUND_SEMI_TRANSPARENT = 3;
    private final byte BACKGROUND_CUSTOM = -1;
    private final int REGULAR = 0;
    private final int SECONDARY = 1;
    private final int ACCENT = 2;
    private final int WHITE = 3;
    private final int GRAY = 4;
    private final int CUSTOM = -1;
    private TypedArray typedArray;
    private byte COLOR_MODE = BACKGROUND_REGULAR;
    private int TINT_MODE = REGULAR;
    
    public CircularImageImageButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircularImageImageButton);
        init();
    }
    
    public CircularImageImageButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircularImageImageButton, defStyleAttr, 0);
        init();
    }
    
    private void init() {
        backgroundDrawable = Utils.getCircularBackgroundDrawable(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        rippleDrawable = Utils.getRippleDrawable(ThemeManager.INSTANCE.getAccent().getSecondaryAccentColor());
        backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setBackground(backgroundDrawable);
        setScaleType(ScaleType.FIT_CENTER);
        setImageTintList(ColorStateList.valueOf(Color.WHITE));
        setForeground(rippleDrawable);
        setElevation(12F);
        
        COLOR_MODE = (byte) typedArray.getInt(R.styleable.CircularImageImageButton_buttonBackgroundType, BACKGROUND_REGULAR);
        TINT_MODE = typedArray.getInt(R.styleable.CircularImageImageButton_buttonTintType, REGULAR);
        
        setBackgroundColor();
        setTint(getTintColor(TINT_MODE), false);
        
        typedArray.recycle();
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        
        valueAnimator = Utils.animateColorChange(backgroundDrawable, accent.getPrimaryAccentColor());
        rippleDrawable.setColor(ColorStateList.valueOf(accent.getSecondaryAccentColor()));
        setBackgroundColor();
        setTint(getTintColor(TINT_MODE), true);
    }
    
    @Override
    public void onThemeChanged(@NonNull Theme theme, boolean animate) {
        ThemeChangedListener.super.onThemeChanged(theme, animate);
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        
        setBackgroundColor();
        setTint(getTintColor(TINT_MODE), animate);
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }
    
    private void setBackgroundColor() {
        switch (COLOR_MODE) {
            case BACKGROUND_REGULAR:
                backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getHighlightColor());
                break;
            case BACKGROUND_SECONDARY:
                backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getTheme().getViewGroupTheme().getSelectedBackgroundColor());
                break;
            case BACKGROUND_CUSTOM:
                // Assuming a custom color is set in the attributes
                break;
            case BACKGROUND_SEMI_TRANSPARENT:
                backgroundDrawable.getPaint().setColor(ColorUtils.INSTANCE.changeAlpha(Color.BLACK, 128));
                break;
            default:
                backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
                break;
        }
    }
    
    private void setTint(int endColor, boolean animate) {
        if (animate) {
            valueAnimator = ValueAnimator.ofArgb(Objects.requireNonNull(getImageTintList()).getDefaultColor(), endColor);
            valueAnimator.setDuration(getResources().getInteger(R.integer.animation_duration));
            valueAnimator.setInterpolator(new DecelerateInterpolator(1.5F));
            valueAnimator.addUpdateListener(animation -> {
                setImageTintList(ColorStateList.valueOf((int) animation.getAnimatedValue()));
                ViewUtils.INSTANCE.addShadow(this, (int) animation.getAnimatedValue());
            });
            valueAnimator.start();
        } else {
            setImageTintList(ColorStateList.valueOf(endColor));
            ViewUtils.INSTANCE.addShadow(this, endColor);
        }
    }
    
    private int getTintColor(int tintMode) {
        return switch (tintMode) {
            case REGULAR ->
                    ThemeManager.INSTANCE.getTheme().getIconTheme().getRegularIconColor();
            case SECONDARY ->
                    ThemeManager.INSTANCE.getTheme().getIconTheme().getSecondaryIconColor();
            case ACCENT ->
                    ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor();
            case WHITE ->
                    Color.WHITE;
            case GRAY ->
                    Color.GRAY;
            default ->
                    Objects.requireNonNull(getImageTintList()).getDefaultColor();
        };
    }
}
