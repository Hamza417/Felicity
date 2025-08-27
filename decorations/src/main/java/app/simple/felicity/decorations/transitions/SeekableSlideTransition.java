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
import app.simple.felicity.decorations.artflow.ArtFlow;
import app.simple.felicity.decorations.pager.FelicityPager;

public class SeekableSlideTransition extends Visibility {
    
    public static final int DECELERATE_FACTOR = 3;
    private static final long DEFAULT_DURATION = 500L;
    private final boolean forward;
    
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
        // Important for predictive back
        return true;
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
        animator.setInterpolator(new DecelerateInterpolator(DECELERATE_FACTOR));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
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
        
        // Reset properties when transition ends or is cancelled
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                view.setTranslationX(0f);
                view.setAlpha(1f);
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                view.setTranslationX(0f);
                view.setAlpha(1f);
            }
        });
        
        return animator;
    }
}
