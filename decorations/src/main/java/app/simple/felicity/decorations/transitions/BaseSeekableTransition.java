package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.Transition;
import androidx.transition.TransitionValues;
import androidx.transition.Visibility;
import app.simple.felicity.decorations.artflow.ArtFlow;

/**
 * Base class for seekable transitions that support predictive back gestures.
 * <p>
 * This class provides common functionality for transitions that need to behave differently
 * during predictive back gestures versus normal animations. The key feature is detecting
 * when the animation is being controlled by a gesture and applying linear interpolation,
 * versus when it runs normally and applies smooth deceleration.
 * <p>
 * Attach a {@link PredictiveBackListener} via {@link #setPredictiveBackListener} to receive
 * callbacks for gesture progress, cancellation, and confirmation without intercepting the
 * back press. This keeps the built-in fragment-manager seekable transition mechanism intact.
 *
 * @author Hamza417
 */
public abstract class BaseSeekableTransition extends Visibility {
    
    /**
     * This flag helps us detect if we're in predictive back gesture mode.
     * When true, the animation is being controlled by the user's finger dragging back.
     * When false, the animation is running normally with smooth deceleration.
     */
    private boolean isBeingControlled = false;

    public static final int DECELERATE_FACTOR = 3;
    protected static final long DEFAULT_DURATION = 500L;
    protected final boolean forward;
    /**
     * Set to true the first time {@link #getProgress} detects that the animator is being
     * seeked by a gesture (not running freely). This survives until the transition ends or
     * is cancelled so the {@link Transition.TransitionListener} can distinguish a
     * predictive-back completion from an ordinary navigation completion.
     */
    private boolean wasEverSeeked = false;
    @Nullable
    private PredictiveBackListener predictiveBackListener;
    
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

    public BaseSeekableTransition(boolean forward) {
        this.forward = forward;
        setDuration(DEFAULT_DURATION);
    }
    
    /**
     * Attaches a {@link PredictiveBackListener} to this transition. The listener will
     * receive gesture progress updates while the back gesture is in flight, a cancellation
     * callback if the user aborts the gesture, and a confirmation callback when the gesture
     * is committed and the transition completes.
     * <p>
     * This method must be called before the transition starts (for example, in
     * {@code ScopedFragment.setTransitions()}).
     *
     * @param listener The listener to attach, or {@code null} to clear the current listener.
     */
    public void setPredictiveBackListener(@Nullable PredictiveBackListener listener) {
        this.predictiveBackListener = listener;
        if (listener == null) {
            return;
        }
        
        addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(@NonNull Transition transition) {
                // Cannot reliably distinguish predictive-back start from normal navigation
                // start at the transition level alone. Use FragmentManager.OnBackStackChangedListener
                // in the fragment for the onStartPredictiveBack() event instead.
            }
            
            @Override
            public void onTransitionEnd(@NonNull Transition transition) {
                if (wasEverSeeked && predictiveBackListener != null) {
                    predictiveBackListener.onPredictiveBackConfirmed();
                }
                wasEverSeeked = false;
            }
            
            @Override
            public void onTransitionCancel(@NonNull Transition transition) {
                if (predictiveBackListener != null) {
                    predictiveBackListener.onPredictiveBackCancelled();
                }
                wasEverSeeked = false;
            }
            
            @Override
            public void onTransitionPause(@NonNull Transition transition) {
                // no-op
            }
            
            @Override
            public void onTransitionResume(@NonNull Transition transition) {
                // no-op
            }
        });
    }

    @Override
    public Animator createAnimator(@NonNull ViewGroup sceneRoot,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * When a transition is created, we assume it might be controlled by a gesture.
         * Reset wasEverSeeked so each new transition instance starts with a clean slate.
         */
        isBeingControlled = true;
        wasEverSeeked = false;
        return super.createAnimator(sceneRoot, startValues, endValues);
    }

    @Override
    public boolean isSeekingSupported() {
        /*
         * This tells Android that this transition can be controlled by predictive back gestures.
         * When enabled, the user can drag back and see the transition happen in real time.
         */
        return true;
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
     * Determines the effective animation progress based on whether the animator is currently
     * being seeked by a predictive back gesture or running freely.
     * <p>
     * When the gesture is actively dragging, {@code isRunning()} returns {@code false}
     * because Android drives the animator with {@code setCurrentPlayTime()} rather than
     * letting it play. In that case, the raw linear progress is returned so the transition
     * follows the finger exactly, and the {@link PredictiveBackListener} receives a progress
     * update.
     * <p>
     * Once the animator is released and plays on its own, a decelerate curve is applied for
     * a smooth, polished feel.
     */
    protected float getProgress(ValueAnimator animation) {
        float rawProgress = (float) animation.getAnimatedValue();
        
        /*
         * The isRunning() check is key. When Android controls the animation with setCurrentPlayTime()
         * during predictive back, isRunning() returns false. When the animation actually plays,
         * isRunning() returns true.
         */
        boolean isRunning = animation.isRunning();
        
        if (isBeingControlled && !isRunning) {
            /*
             * Predictive back mode: use raw linear progress so the transition follows the
             * finger drag perfectly. Also record that this transition was seeked so the
             * TransitionListener can confirm it was a predictive back gesture.
             */
            wasEverSeeked = true;
            if (predictiveBackListener != null) {
                predictiveBackListener.onPredictiveBackProgressed(rawProgress);
            }
            return rawProgress;
        } else {
            /*
             * Normal animation mode: apply the decelerate curve for a smooth, professional
             * feel. The animation starts fast and slows down at the end.
             */
            isBeingControlled = false;
            DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(DECELERATE_FACTOR);
            return decelerateInterpolator.getInterpolation(rawProgress);
        }
    }

    /**
     * Callback interface for predictive back gesture events that originate from
     * a seekable transition. Listeners receive progress, cancel, and confirm events
     * without intercepting the gesture, so the native seeking behavior is preserved.
     */
    public interface PredictiveBackListener {
        
        /**
         * Called each frame while the back gesture is actively seeking this transition.
         *
         * @param progress A normalized value in the range [0.0, 1.0] representing how
         *                 far the back gesture has progressed.
         */
        void onPredictiveBackProgressed(float progress);
        
        /**
         * Called when the back gesture is cancelled and this transition reverses to its
         * original state. Use this to restore any UI state that was changed speculatively
         * during the gesture (for example, mini-player visibility or status-bar colors).
         */
        void onPredictiveBackCancelled();
        
        /**
         * Called when the back gesture is committed and this transition completes normally.
         * The fragment lifecycle will subsequently restore state through its own events.
         */
        void onPredictiveBackConfirmed();
    }
    
    /**
     * Marks that the animation is no longer being controlled.
     * Should be called when animation ends or is canceled.
     */
    protected void resetControlFlag() {
        isBeingControlled = false;
    }
}

