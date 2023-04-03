package app.simple.felicity.decorations.padding;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import app.simple.felicity.decorations.theme.ThemeConstraintLayout;
import app.simple.felicity.utils.StatusBarHeight;

public class PaddingAwareConstraintLayout extends ThemeConstraintLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    public PaddingAwareConstraintLayout(Context context) {
        super(context);
        init();
    }
    
    public PaddingAwareConstraintLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        if (isInEditMode()) {
            return;
        }
        updatePadding();
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    private void updatePadding() {
        setPadding(getPaddingLeft(),
                StatusBarHeight.getStatusBarHeight(getResources()) + getPaddingTop(),
                getPaddingRight(),
                getPaddingBottom());
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
