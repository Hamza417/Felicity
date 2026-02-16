package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.transition.TransitionValues;
import androidx.transition.Visibility;
import app.simple.felicity.decorations.artflow.ArtFlow;
import app.simple.felicity.decorations.pager.FelicityPager;

/**
 * Base class for seekable transitions that support predictive back gestures.
 * <p>
 * This class provides common functionality for transitions that need to behave differently
 * during predictive back gestures versus normal animations. The key feature is detecting
 * when the animation is being controlled by a gesture and applying linear interpolation,
 * versus when it runs normally and applies smooth deceleration.
 */
public abstract class BaseSeekableTransition extends Visibility {
    
    public static final int DECELERATE_FACTOR = 3;
    protected static final long DEFAULT_DURATION = 500L;
    protected final boolean forward;
    
    /*
     * This flag helps us detect if we're in predictive back gesture mode.
     * When true, the animation is being controlled by the user's finger dragging back.
     * When false, the animation is running normally with smooth deceleration.
     */
    private boolean isBeingControlled = false;
    
    public BaseSeekableTransition(boolean forward) {
        this.forward = forward;
        setDuration(DEFAULT_DURATION);
    }
    
    /**
     * Finds an ArtFlow view in the view hierarchy and sets its alpha to 0.
     * This allows us to control the alpha during the transition.
     */
    protected static ArtFlow findCoverFlow(View root) {
        if (root instanceof ArtFlow) {
            root.setAlpha(0f);
            return (ArtFlow) root;
        }
        
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                ArtFlow found = findCoverFlow(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
    
    /**
     * Finds a FelicityPager view in the view hierarchy and sets its alpha to 0.
     * This allows us to control the alpha during the transition.
     */
    protected static FelicityPager findFelicityPager(View root) {
        if (root instanceof FelicityPager) {
            root.setAlpha(0f);
            return (FelicityPager) root;
        }
        
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                FelicityPager found = findFelicityPager(group.getChildAt(i));
                if (found != null) {
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
    
    /**
     * Creates a ValueAnimator with proper configuration for predictive back support.
     * Subclasses should use this to create their animators.
     */
    protected ValueAnimator createBaseAnimator() {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEFAULT_DURATION);
        
        /*
         * We use a linear interpolator on the animator itself because during predictive back,
         * Android controls the animation by setting its current time directly.
         * We'll manually apply the decelerate effect when the animation actually runs.
         */
        animator.setInterpolator(new LinearInterpolator());
        
        return animator;
    }
    
    /**
     * This method determines what kind of progress to use based on how the animation is being used.
     * <p>
     * When you drag back with predictive gesture, the animation is "controlled" but not "running".
     * We use linear progress here so the transition follows your finger exactly.
     * <p>
     * When the animation actually runs (normal navigation or after releasing the gesture),
     * we apply a decelerate curve for a smooth, polished feel.
     */
    protected float getProgress(ValueAnimator animation) {
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
    
    /**
     * Marks that the animation is no longer being controlled.
     * Should be called when animation ends or is canceled.
     */
    protected void resetControlFlag() {
        isBeingControlled = false;
    }
}

