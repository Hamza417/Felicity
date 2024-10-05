package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class SquareView extends View {
    public SquareView(Context context) {
        super(context);
    }
    
    public SquareView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }
    
    public SquareView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size;
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ^ MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                size = width;
            } else {
                size = height;
            }
        } else {
            size = Math.min(width, height);
        }
        setMeasuredDimension(size, size);
    }
}
