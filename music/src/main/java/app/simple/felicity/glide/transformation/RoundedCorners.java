package app.simple.felicity.glide.transformation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;

import java.security.MessageDigest;

import androidx.annotation.NonNull;

public class RoundedCorners extends BitmapTransformation {
    
    private static final int VERSION = 1;
    private static final String ID = "RoundedCorner." + VERSION;
    
    private final int radius;
    private final int margin;
    
    public RoundedCorners(int radius, int margin) {
        this.radius = radius;
        this.margin = margin;
    }
    
    public RoundedCorners(int radius) {
        this(radius, 0);
    }
    
    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    @Override
    protected Bitmap transform(@NonNull Context context, @NonNull BitmapPool pool,
            @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int width = toTransform.getWidth();
        int height = toTransform.getHeight();
        
        int pixelRadius = dpToPx(context, radius);
        int pixelMargin = dpToPx(context, margin);
        
        Bitmap bitmap = pool.get(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        
        setCanvasBitmapDensity(toTransform, bitmap);
        
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        
        float right = width - pixelMargin;
        float bottom = height - pixelMargin;
        canvas.drawRoundRect(new RectF(pixelMargin, pixelMargin, right, bottom),
                pixelRadius, pixelRadius, paint);
        
        return bitmap;
    }
    
    @NonNull
    @Override
    public String toString() {
        return "RoundedCorner(radius=" + radius + ", margin=" + margin + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof RoundedCorners &&
                ((RoundedCorners) o).radius == radius &&
                ((RoundedCorners) o).margin == margin;
    }
    
    @Override
    public int hashCode() {
        return ID.hashCode() + radius * 10000 + margin * 100;
    }
    
    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update((ID + radius + margin).getBytes(CHARSET));
    }
}