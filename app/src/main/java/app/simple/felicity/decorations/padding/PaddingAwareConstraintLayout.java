package app.simple.felicity.decorations.padding;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import app.simple.felicity.decorations.theme.ThemeConstraintLayout;

public class PaddingAwareConstraintLayout extends ThemeConstraintLayout implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private boolean statusPaddingRequired = true;
    private boolean navigationPaddingRequired = true;
    
    public PaddingAwareConstraintLayout(Context context) {
        super(context);
        init(null);
    }
    
    public PaddingAwareConstraintLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, app.simple.felicity.R.styleable.PaddingAwareConstraintLayout)) {
            statusPaddingRequired = typedArray.getBoolean(app.simple.felicity.R.styleable.PaddingAwareConstraintLayout_statusPaddingRequired, true);
            navigationPaddingRequired = typedArray.getBoolean(app.simple.felicity.R.styleable.PaddingAwareConstraintLayout_navigationPaddingRequired, true);
            
            Utils.applySystemBarPadding(this, statusPaddingRequired, navigationPaddingRequired);
        }
        
        if (isInEditMode()) {
            return;
        }
        
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
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
