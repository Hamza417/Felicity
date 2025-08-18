package app.simple.felicity.decorations.padding;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import androidx.annotation.Nullable;
import app.simple.felicity.core.utils.BarHeight;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.theme.ThemeLinearLayout;

public class PaddingAwareLinearLayout extends ThemeLinearLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private boolean statusPaddingRequired = true;
    private boolean navigationPaddingRequired = true;
    
    public PaddingAwareLinearLayout(Context context) {
        super(context);
        init(null);
    }
    
    public PaddingAwareLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public PaddingAwareLinearLayout(@NotNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    @SuppressLint ("CustomViewStyleable")
    private void init(AttributeSet attrs) {
        try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PaddingAwareLinearLayout)) {
            statusPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareLinearLayout_statusPaddingRequired, true);
            navigationPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareLinearLayout_navigationPaddingRequired, true);
        }
        
        int statusBarHeight = BarHeight.getStatusBarHeight(getResources());
        int navigationBarHeight = BarHeight.getNavigationBarHeight(getResources());
        
        if (statusPaddingRequired) {
            setPadding(getPaddingLeft(), statusBarHeight + getPaddingTop(), getPaddingRight(), getPaddingBottom());
        }
        
        if (navigationPaddingRequired) {
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), navigationBarHeight + getPaddingBottom());
        }
        
        if (isInEditMode()) {
            return;
        }
        
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @TestOnly
    private void setLayoutTransitions() {
        setLayoutTransition(new LayoutTransition());
        getLayoutTransition().setDuration(getResources().getInteger(R.integer.animation_duration));
        getLayoutTransition().setInterpolator(LayoutTransition.CHANGE_APPEARING, new DecelerateInterpolator(1.5F));
        getLayoutTransition().setInterpolator(LayoutTransition.CHANGE_DISAPPEARING, new DecelerateInterpolator(1.5F));
        getLayoutTransition().setInterpolator(LayoutTransition.CHANGING, new DecelerateInterpolator(1.5F));
        getLayoutTransition().setInterpolator(LayoutTransition.APPEARING, new DecelerateInterpolator(1.5F));
        getLayoutTransition().setInterpolator(LayoutTransition.DISAPPEARING, new DecelerateInterpolator(1.5F));
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
