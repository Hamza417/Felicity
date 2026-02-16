package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.transition.TransitionValues;
import app.simple.felicity.decorations.artflow.ArtFlow;
import app.simple.felicity.decorations.pager.FelicityPager;

/**
 * A seekable transition that animates fragments with a shared axis Z effect.
 * Fragments scale and fade in/out along the Z axis.
 * <p>
 * Supports predictive back gestures for smooth, responsive navigation.
 */
public class SeekableSharedAxisZTransition extends BaseSeekableTransition {
    
    private static final float SCALE_IN_FROM = 0.5f;
    private static final float SCALE_OUT_TO = 1.5f;
    
    public SeekableSharedAxisZTransition(boolean forward) {
        super(forward);
    }
    
    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * Entering fragment scales from behind and fades in.
         * When going forward, start small. When going back, start large.
         */
        float startScale = forward ? SCALE_IN_FROM : SCALE_OUT_TO;
        float endScale = 1f;
        return createAnimator(view, startScale, endScale, 0f, 1f);
    }
    
    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        /*
         * Exiting fragment scales away and fades out.
         * When going forward, scale up. When going back, scale down.
         */
        float endScale = forward ? SCALE_OUT_TO : SCALE_IN_FROM;
        float startScale = 1f;
        return createAnimator(view, startScale, endScale, 1f, 0f);
    }
    
    private Animator createAnimator(
            final View view,
            final float startScale,
            final float endScale,
            final float startAlpha,
            final float endAlpha) {
        
        ArtFlow artFlow = findCoverFlow(view);
        FelicityPager felicityPager = findFelicityPager(view);
        
        ValueAnimator animator = createBaseAnimator();
        
        animator.addUpdateListener(animation -> {
            /*
             * Here's where the magic happens! We check if we're being controlled by a gesture
             * or running normally, then apply the appropriate progress calculation.
             */
            float progress = getProgress(animation);
            
            float scale = startScale + (endScale - startScale) * progress;
            float alpha = startAlpha + (endAlpha - startAlpha) * progress;
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(alpha);
            
            if (artFlow != null) {
                artFlow.setAlpha(alpha);
            }
            
            if (felicityPager != null) {
                felicityPager.setAlpha(alpha);
            }
        });
        
        /*
         * Clean up when the animation finishes or gets cancelled.
         * Also reset our control flag so the next transition starts fresh.
         */
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
                resetControlFlag();
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
                resetControlFlag();
            }
        });
        
        return animator;
    }
}

