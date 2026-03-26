package app.simple.felicity.decorations.views;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import app.simple.felicity.preferences.AppearancePreferences;

/**
 * A {@link RecyclerView} that clips all of its children to a rounded rectangle
 * whose corner radius is sourced from {@link AppearancePreferences#getCornerRadius()}.
 * Ideal for building small, custom filled lists that need rounded corners without
 * wrapping the list in an additional clipping container.
 *
 * <p>The clip is applied at two levels:</p>
 * <ul>
 *     <li>Canvas-level via {@link #dispatchDraw(Canvas)} for accurate child clipping.</li>
 *     <li>Outline-level via {@link ViewOutlineProvider} so elevation shadows follow
 *     the rounded shape.</li>
 * </ul>
 *
 * <p>The corner radius updates automatically when the corresponding shared preference
 * ({@link AppearancePreferences#APP_CORNER_RADIUS}) changes.</p>
 *
 * @author Hamza417
 */
public class CornerRadiusRectRecyclerView extends RecyclerView
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private final Path clipPath = new Path();
    private float cornerRadius;
    
    public CornerRadiusRectRecyclerView(@NonNull Context context) {
        super(context);
        init();
    }
    
    public CornerRadiusRectRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public CornerRadiusRectRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    /**
     * Initializes the corner radius from preferences and configures the outline provider
     * so that elevation shadows conform to the rounded shape.
     */
    private void init() {
        if (isInEditMode()) {
            return;
        }
        
        cornerRadius = AppearancePreferences.INSTANCE.getCornerRadius();
        
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, @NonNull Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildClipPath(w, h);
    }
    
    /**
     * Reconstructs the rounded-rectangle clip path for the given dimensions.
     *
     * @param width  Current view width in pixels.
     * @param height Current view height in pixels.
     */
    private void rebuildClipPath(int width, int height) {
        clipPath.reset();
        clipPath.addRoundRect(
                new RectF(0F, 0F, width, height),
                cornerRadius,
                cornerRadius,
                Path.Direction.CW);
    }
    
    /**
     * Clips the canvas to the rounded-rectangle path before delegating to the
     * standard child-drawing routine, ensuring no child content bleeds outside
     * the rounded boundary.
     *
     * @param canvas The canvas on which to draw.
     */
    @Override
    public void dispatchDraw(@NonNull Canvas canvas) {
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }
    
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            app.simple.felicity.manager.SharedPreferences.INSTANCE
                    .getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(this);
        }
    }
    
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        app.simple.felicity.manager.SharedPreferences.INSTANCE
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }
    
    /**
     * Responds to shared preference changes. When {@link AppearancePreferences#APP_CORNER_RADIUS}
     * changes, the clip path and outline are rebuilt and the view is invalidated so the
     * new radius takes effect immediately.
     *
     * @param sharedPreferences The shared preferences that changed.
     * @param key               The key of the preference that changed.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (AppearancePreferences.APP_CORNER_RADIUS.equals(key)) {
            cornerRadius = AppearancePreferences.INSTANCE.getCornerRadius();
            rebuildClipPath(getWidth(), getHeight());
            invalidateOutline();
            invalidate();
        }
    }
}
