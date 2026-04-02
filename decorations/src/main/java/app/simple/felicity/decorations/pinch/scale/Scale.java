package app.simple.felicity.decorations.pinch.scale;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import androidx.annotation.IntDef;

public interface Scale {
    int TYPE_SCALE_UP = 1;
    int TYPE_SCALE_DOWN = 0;
    
    void updateScale(float incrementalScale);
    
    float getScale();
    
    @Type
    int getType();
    
    @IntDef (value = {TYPE_SCALE_DOWN, TYPE_SCALE_UP})
    @Retention (RetentionPolicy.SOURCE)
    @interface Type {
    
    }
}
