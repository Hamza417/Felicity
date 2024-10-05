package app.simple.felicity.decorations.padding;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.fastscroll.FastScrollNestedScrollView;

public class PaddingAwareNestedScrollView extends FastScrollNestedScrollView implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private boolean statusPaddingRequired = true;
    private boolean navigationPaddingRequired = true;
    
    public PaddingAwareNestedScrollView(@NonNull Context context) {
        super(context);
        init(null);
    }
    
    public PaddingAwareNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    private void init(AttributeSet attrs) {
        try (TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PaddingAwareNestedScrollView)) {
            statusPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareNestedScrollView_statusPaddingRequired, true);
            navigationPaddingRequired = typedArray.getBoolean(R.styleable.PaddingAwareNestedScrollView_navigationPaddingRequired, true);
            
            Utils.applySystemBarPadding(this, statusPaddingRequired, navigationPaddingRequired);
        }
        
        if (isInEditMode()) {
            return;
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        app.simple.felicity.preferences.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
