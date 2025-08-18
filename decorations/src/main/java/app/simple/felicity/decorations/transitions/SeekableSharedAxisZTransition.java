package app.simple.felicity.decorations.transitions;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.transition.TransitionValues;
import androidx.transition.Visibility;
import app.simple.felicity.decorations.coverflow.CoverFlow;
import app.simple.felicity.decorations.pager.FelicityPager;

public class SeekableSharedAxisZTransition extends Visibility {
    public static final int DECELERATE_FACTOR = 3;
    private static final float SCALE_IN_FROM = 0.5f;
    private static final float SCALE_OUT_TO = 1.5f;
    private static final long DEFAULT_DURATION = 500L;
    private final boolean forward;
    
    public SeekableSharedAxisZTransition(boolean forward) {
        this.forward = forward;
        setDuration(DEFAULT_DURATION);
    }
    
    @Override
    public boolean isSeekingSupported() {
        // Important for predictive back
        return true;
    }
    
    private static CoverFlow findCoverFlow(View root) {
        if (root instanceof CoverFlow) {
            root.setAlpha(0f);
            return (CoverFlow) root;
        }
        
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                CoverFlow found = findCoverFlow(group.getChildAt(i));
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
    
    private Animator createAnimator(final View view, final float startScale, final float endScale,
            final float startAlpha, final float endAlpha) {
        CoverFlow coverFlow = findCoverFlow(view);
        FelicityPager felicityPager = findFelicityPager(view);
        
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DEFAULT_DURATION);
        animator.setInterpolator(new DecelerateInterpolator(DECELERATE_FACTOR));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            float scale = startScale + (endScale - startScale) * progress;
            float alpha = startAlpha + (endAlpha - startAlpha) * progress;
            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(alpha);
            
            if (coverFlow != null) {
                coverFlow.setAlpha(alpha);
            }
            
            if (felicityPager != null) {
                felicityPager.setAlpha(alpha);
            }
        });
        
        // Reset properties when transition ends or is cancelled
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                view.setScaleX(1f);
                view.setScaleY(1f);
                view.setAlpha(1f);
            }
        });
        
        return animator;
    }
}
