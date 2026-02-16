package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.transition.TransitionValues;
import androidx.transition.Visibility;
import app.simple.felicity.decorations.artflow.ArtFlow;
import app.simple.felicity.decorations.pager.FelicityPager;

public class SeekableSharedAxisZTransition extends Visibility {
    
    public static final int DECELERATE_FACTOR = 3;
    private static final float SCALE_IN_FROM = 0.5f;
    private static final float SCALE_OUT_TO = 1.5f;
    private static final long DEFAULT_DURATION = 500L;
    private final boolean forward;
    private boolean isBeingControlled = false;
    
    public SeekableSharedAxisZTransition(boolean forward) {
        this.forward = forward;
        setDuration(DEFAULT_DURATION);
    }
    
    @Override
    public boolean isSeekingSupported() {
        // Important for predictive back
        return true;
    }
    
    @Override
    public Animator createAnimator(@NonNull ViewGroup sceneRoot,
            TransitionValues startValues,
            TransitionValues endValues) {
        // When this is called, mark that we're potentially in controlled/seeking mode
        // We'll reset this flag when the animation actually starts running
        isBeingControlled = true;
        return super.createAnimator(sceneRoot, startValues, endValues);
    }
    
    private static ArtFlow findCoverFlow(View root) {
        if (root instanceof ArtFlow) {
            root.setAlpha(0f);
            return (ArtFlow) root;
        }
        
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                ArtFlow found = findCoverFlow(group.getChildAt(i));
                if (found != null) {
                    Log.i("SeekableSharedAxisZTransition", "Found CoverFlow: " + found.getClass().getSimpleName());
                    return found;
                }
            }
        }
        return null;
    }
    
    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        // Entering: scale from behind, fade in
        float startScale = forward ? SCALE_IN_FROM : SCALE_OUT_TO;
        float endScale = 1f;
        return createAnimator(view, startScale, endScale, 0f, 1f);
    }
    
    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        // Exiting: scale out, fade out
        float endScale = forward ? SCALE_OUT_TO : SCALE_IN_FROM;
        float startScale = 1f;
        return createAnimator(view, startScale, endScale, 1f, 0f);
    }
    
    private static FelicityPager findFelicityPager(View root) {
        if (root instanceof FelicityPager) {
            root.setAlpha(0f);
            return (FelicityPager) root;
        }
        
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                FelicityPager found = findFelicityPager(group.getChildAt(i));
                if (found != null) {
                    Log.i("SeekableSharedAxisZTransition", "Found FelicityPager: " + found.getClass().getSimpleName());
                    return found;
                }
            }
        }
        return null;
    }
    
    private Animator createAnimator(
            final View view,
            final float startScale,
            final float endScale,
            final float startAlpha,
            final float endAlpha) {
        
        ArtFlow artFlow = findCoverFlow(view);
        FelicityPager felicityPager = findFelicityPager(view);
        
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEFAULT_DURATION);
        
        // Use LinearInterpolator for the animator itself
        // We'll manually apply decelerate when not being controlled
        animator.setInterpolator(new LinearInterpolator());
        
        animator.addUpdateListener(animation -> {
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
        
        // Reset properties when transition ends or is canceled
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
                isBeingControlled = false;
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
                isBeingControlled = false;
            }
        });
        
        return animator;
    }
    
    /*
     * This method determines what kind of progress to use based on how the animation is being used.
     *
     * When you drag back with predictive gesture, the animation is "controlled" but not "running".
     * We use linear progress here so the transition follows your finger exactly.
     *
     * When the animation actually runs (normal navigation or after releasing the gesture),
     * we apply a decelerate curve for a smooth, polished feel.
     */
    private float getProgress(ValueAnimator animation) {
        float rawProgress = (float) animation.getAnimatedValue();
        
        /*
         * The isRunning() check is key! When Android controls the animation with setCurrentPlayTime()
         * during predictive back, isRunning() returns false. When the animation actually plays,
         * isRunning() returns true. Perfect for our needs!
         */
        boolean isRunning = animation.isRunning();
        
        if (isBeingControlled && !isRunning) {
            /*
             * Predictive back mode! Use raw linear progress so the transition
             * follows your finger drag perfectly. No interpolation curve applied.
             */
            return rawProgress;
        } else {
            /*
             * Normal animation mode! Apply the decelerate curve for that smooth,
             * professional feel. The animation starts fast and slows down at the end.
             */
            isBeingControlled = false;
            DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(DECELERATE_FACTOR);
            return decelerateInterpolator.getInterpolation(rawProgress);
        }
    }
}
