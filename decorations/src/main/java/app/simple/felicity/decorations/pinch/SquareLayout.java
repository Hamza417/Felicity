package app.simple.felicity.decorations.pinch;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class SquareLayout extends FrameLayout {
    public SquareLayout(Context context) {
        this(context, null, 0);
    }
    
    public SquareLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    
    public SquareLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}