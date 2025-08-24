package app.simple.felicity.decorations.padding;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.theme.ThemeFrameLayout;

public class PaddingAwareFrameLayout extends ThemeFrameLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private boolean statusPaddingRequired = true;
    private boolean navigationPaddingRequired = true;
    
    public PaddingAwareFrameLayout(@NonNull Context context) {
        super(context);
        init(null);
    }
    
    public PaddingAwareFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PaddingAwareFrameLayout)) {
            statusPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareFrameLayout_statusPaddingRequired, true);
            navigationPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareFrameLayout_navigationPaddingRequired, true);
            
            Utils.applySystemBarPadding(this, statusPaddingRequired, navigationPaddingRequired);
        }
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
