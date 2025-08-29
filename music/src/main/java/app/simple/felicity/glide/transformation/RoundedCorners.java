package app.simple.felicity.glide.transformation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
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
    
    @NonNull
    private static Paint getPaint(@NonNull Bitmap toTransform, int targetHeight, int targetWidth) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(toTransform, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        
        // Center-crop the source into the target size so radius is applied at final resolution
        float scale;
        float dx = 0f, dy = 0f;
        if (toTransform.getWidth() * targetHeight > targetWidth * toTransform.getHeight()) {
            scale = (float) targetHeight / (float) toTransform.getHeight();
            dx = (targetWidth - toTransform.getWidth() * scale) * 0.5f;
        } else {
            scale = (float) targetWidth / (float) toTransform.getWidth();
            dy = (targetHeight - toTransform.getHeight() * scale) * 0.5f;
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        
        return paint;
    }
    
    /**
     * @noinspection UnnecessaryLocalVariable
     */
    @Override
    protected Bitmap transform(@NonNull Context context, @NonNull BitmapPool pool,
            @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        // Target output dimensions from Glide so rounding appears consistent on-screen
        int targetWidth = outWidth;
        int targetHeight = outHeight;
        
        int pixelRadius = dpToPx(context, radius);
        int pixelMargin = dpToPx(context, margin);
        
        // Ensure radius doesn't exceed half of the shortest side after accounting for margin
        int maxAllowedRadius = Math.max(0, (Math.min(targetWidth, targetHeight) - 2 * pixelMargin) / 2);
        if (pixelRadius > maxAllowedRadius) {
            pixelRadius = maxAllowedRadius;
        }
        
        Bitmap bitmap = pool.get(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        
        setCanvasBitmapDensity(toTransform, bitmap);
        
        Canvas canvas = new Canvas(bitmap);
        Paint paint = getPaint(toTransform, targetHeight, targetWidth);
        
        float left = pixelMargin;
        float top = pixelMargin;
        float right = targetWidth - pixelMargin;
        float bottom = targetHeight - pixelMargin;
        canvas.drawRoundRect(new RectF(left, top, right, bottom), pixelRadius, pixelRadius, paint);
        
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