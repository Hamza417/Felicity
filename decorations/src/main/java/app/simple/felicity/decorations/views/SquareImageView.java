package app.simple.felicity.decorations.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class SquareImageView extends AppCompatImageView {
    public SquareImageView(Context context) {
        super(context);
        init();
    }
    
    public SquareImageView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public SquareImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    private void init() {
        setClipToOutline(false);
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
