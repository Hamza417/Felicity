package app.simple.felicity.decorations.circular;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.widget.TextViewCompat;
import app.simple.felicity.core.utils.ViewUtils;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.utils.TypeFace;
import app.simple.felicity.theme.interfaces.ThemeChangedListener;
import app.simple.felicity.theme.managers.ThemeManager;
import app.simple.felicity.theme.models.Accent;

public class CircularTextButton extends AppCompatTextView implements ThemeChangedListener {
    
    private ShapeDrawable backgroundDrawable;
    private RippleDrawable rippleDrawable;
    private ValueAnimator valueAnimator;
    
    public CircularTextButton(@NonNull Context context) {
        super(context);
        init();
    }
    
    public CircularTextButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CircularTextButton(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        backgroundDrawable = Utils.getCircularBackgroundDrawable(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        rippleDrawable = Utils.getRippleDrawable(ThemeManager.INSTANCE.getAccent().getSecondaryAccentColor());
        backgroundDrawable.getPaint().setColor(ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
        setBackground(backgroundDrawable);
        setTextColor(Color.WHITE);
        setForeground(rippleDrawable);
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(Color.WHITE));
        setAllCaps(false);
        
        int padding = getResources().getDimensionPixelSize(R.dimen.padding_10);
        setPaddingRelative(padding, padding, padding + padding, padding);
        setCompoundDrawablePadding(padding);
        setTypeface(TypeFace.INSTANCE.getBoldTypeFace(getContext()));
        setElevation(12F);
        ViewUtils.INSTANCE.addShadow(this, ThemeManager.INSTANCE.getAccent().getPrimaryAccentColor());
    }
    
    @Override
    public void onAccentChanged(@NonNull Accent accent) {
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
        
        valueAnimator = Utils.animateColorChange(backgroundDrawable, accent.getPrimaryAccentColor());
        rippleDrawable.setColor(ColorStateList.valueOf(accent.getSecondaryAccentColor()));
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueAnimator != null) {
            valueAnimator.cancel();
            valueAnimator = null;
        }
    }
}
