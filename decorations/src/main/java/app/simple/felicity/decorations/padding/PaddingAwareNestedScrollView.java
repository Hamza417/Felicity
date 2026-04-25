package app.simple.felicity.decorations.padding;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.simple.felicity.decoration.R;
import app.simple.felicity.decorations.theme.ThemeNestedScrollView;

public class PaddingAwareNestedScrollView extends ThemeNestedScrollView implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private boolean statusPaddingRequired = true;
    private boolean navigationPaddingRequired = true;
    
    /**
     * Any extra bottom padding (in pixels) that an outside caller — like the floating
     * mini-player — needs to reserve at the bottom of this scroll view. This value is
     * baked into every insets callback so it survives the system re-dispatching window
     * insets (e.g. when the fragment becomes visible again after navigating back to it).
     * <p>
     * Use {@link #setExtraBottomPadding(int)} to update this value; it automatically
     * triggers a fresh insets pass so the new padding takes effect right away.
     */
    private int extraBottomPadding = 0;
    
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
            
            Utils.applySystemBarPadding(this, statusPaddingRequired, navigationPaddingRequired, () -> extraBottomPadding);
        }
        
        if (isInEditMode()) {
            return;
        }
    }
    
    /**
     * Returns the extra bottom padding currently reserved by external callers.
     */
    public int getExtraBottomPadding() {
        return extraBottomPadding;
    }
    
    /**
     * Sets extra bottom padding that is added on top of whatever the system bar insets
     * contribute. This is the right way for the mini-player (or anything else) to add
     * footer spacing here — it won't get wiped out the next time the OS fires a fresh
     * insets event.
     * <p>
     * Calling this automatically requests a new insets pass so the change shows up
     * immediately without waiting for the next system event.
     *
     * @param padding extra padding in pixels, or 0 to clear it
     */
    public void setExtraBottomPadding(int padding) {
        if (extraBottomPadding != padding) {
            extraBottomPadding = padding;
            // Re-trigger the insets listener so our new extra padding is applied right away.
            ViewCompat.requestApplyInsets(this);
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        app.simple.felicity.manager.SharedPreferences.INSTANCE.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
