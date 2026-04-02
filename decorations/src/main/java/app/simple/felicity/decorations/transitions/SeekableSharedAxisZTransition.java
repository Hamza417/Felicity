package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.transition.TransitionValues;
import app.simple.felicity.decorations.artflow.ArtFlow;

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
        });
        
        /*
         * Clean up when the animation finishes or gets cancelled.
         *
         * onAnimationEnd fires for both forward completion (progress → 1) and predictive
         * back cancel reversal (progress → 0). The threshold of 0.5 reliably distinguishes
         * which case occurred and ensures the view lands in its correct terminal state.
         *
         * onAnimationCancel fires only for programmatic interruptions (not predictive back
         * cancels, which play the animator naturally back to 0). In that case the initial
         * state is always the safe fallback.
         */
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                float p = (float) ((ValueAnimator) animation).getAnimatedValue();
                if (p >= 0.5f) {
                    view.setScaleX(endScale);
                    view.setScaleY(endScale);
                    view.setAlpha(endAlpha);
                    if (artFlow != null) {
                        artFlow.setAlpha(endAlpha);
                    }
                } else {
                    view.setScaleX(startScale);
                    view.setScaleY(startScale);
                    view.setAlpha(startAlpha);
                    if (artFlow != null) {
                        artFlow.setAlpha(startAlpha);
                    }
                }
                resetControlFlag();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
                view.setScaleX(startScale);
                view.setScaleY(startScale);
                view.setAlpha(startAlpha);
                if (artFlow != null) {
                    artFlow.setAlpha(startAlpha);
                }
                resetControlFlag();
            }
        });
        
        return animator;
    }
}

