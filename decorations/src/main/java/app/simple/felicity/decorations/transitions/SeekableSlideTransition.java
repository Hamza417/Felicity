package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
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

public class SeekableSlideTransition extends Visibility {
    
    public static final int DECELERATE_FACTOR = 3;
    private static final long DEFAULT_DURATION = 500L;
    private final boolean forward;
    
    /*
     * This flag helps us detect if we're in predictive back gesture mode.
     * When true, the animation is being controlled by the user's finger dragging back.
     * When false, the animation is running normally with smooth deceleration.
     */
    private boolean isBeingControlled = false;
    
    public SeekableSlideTransition(boolean forward) {
        this.forward = forward;
        setDuration(DEFAULT_DURATION);
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
                    Log.i("SeekableSlideTransition", "Found CoverFlow: " + found.getClass().getSimpleName());
                    return found;
                }
            }
        }
        return null;
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
                    Log.i("SeekableSlideTransition", "Found FelicityPager: " + found.getClass().getSimpleName());
                    return found;
                }
            }
        }
        return null;
    }
    
    @Override
    public boolean isSeekingSupported() {
        /*
         * This tells Android that this transition can be controlled by predictive back gestures.
         * When enabled, the user can drag back and see the transition happen in real time.
         */
        return true;
    }
    
    @Override
    public Animator createAnimator(@NonNull ViewGroup sceneRoot,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * When a transition is created, we assume it might be controlled by a gesture.
         * This flag will be cleared once we detect the animation is actually running.
         */
        isBeingControlled = true;
        return super.createAnimator(sceneRoot, startValues, endValues);
    }
    
    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        // Entering: slide in horizontally and fade in
        int distance = sceneRoot.getWidth();
        float startTranslationX = forward ? distance : -distance;
        float endTranslationX = 0f;
        return createAnimator(view, startTranslationX, endTranslationX, 0f, 1f);
    }
    
    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        // Exiting: slide out horizontally and fade out
        int distance = sceneRoot.getWidth();
        float startTranslationX = 0f;
        float endTranslationX = forward ? -distance : distance;
        return createAnimator(view, startTranslationX, endTranslationX, 1f, 0f);
    }
    
    private Animator createAnimator(final View view, final float startTranslationX, final float endTranslationX,
            final float startAlpha, final float endAlpha) {
        ArtFlow artFlow = findCoverFlow(view);
        FelicityPager felicityPager = findFelicityPager(view);
        
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEFAULT_DURATION);
        
        /*
         * We use a linear interpolator on the animator itself because during predictive back,
         * Android controls the animation by setting its current time directly.
         * We'll manually apply the decelerate effect when the animation actually runs.
         */
        animator.setInterpolator(new LinearInterpolator());
        
        animator.addUpdateListener(animation -> {
            /*
             * Here's where the magic happens! We check if we're being controlled by a gesture
             * or running normally, then apply the appropriate progress calculation.
             */
            float progress = getProgress(animation);
            
            float translation = startTranslationX + (endTranslationX - startTranslationX) * progress;
            float alpha = startAlpha + (endAlpha - startAlpha) * progress;
            view.setTranslationX(translation);
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
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setTranslationX(0f);
                view.setAlpha(1f);
                isBeingControlled = false;
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                view.setTranslationX(0f);
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
