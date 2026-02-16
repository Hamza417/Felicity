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
 * A seekable transition that slides fragments horizontally with fade in/out.
 * <p>
 * Supports predictive back gestures for smooth, responsive navigation.
 */
public class SeekableSlideTransition extends BaseSeekableTransition {
    
    public SeekableSlideTransition(boolean forward) {
        super(forward);
    }
    
    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * Entering fragment slides in horizontally and fades in.
         * When going forward, slide from right. When going back, slide from left.
         */
        int distance = sceneRoot.getWidth();
        float startTranslationX = forward ? distance : -distance;
        float endTranslationX = 0f;
        return createAnimator(view, startTranslationX, endTranslationX, 0f, 1f);
    }
    
    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        /*
         * Exiting fragment slides out horizontally and fades out.
         * When going forward, slide to left. When going back, slide to right.
         */
        int distance = sceneRoot.getWidth();
        float startTranslationX = 0f;
        float endTranslationX = forward ? -distance : distance;
        return createAnimator(view, startTranslationX, endTranslationX, 1f, 0f);
    }
    
    private Animator createAnimator(final View view, final float startTranslationX, final float endTranslationX,
            final float startAlpha, final float endAlpha) {
        ArtFlow artFlow = findCoverFlow(view);
        FelicityPager felicityPager = findFelicityPager(view);
        
        ValueAnimator animator = createBaseAnimator();
        
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
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setTranslationX(0f);
                view.setAlpha(1f);
                resetControlFlag();
            }
            
            @Override
            public void onAnimationCancel(Animator animation) {
                view.setTranslationX(0f);
                view.setAlpha(1f);
                resetControlFlag();
            }
        });
        
        return animator;
    }
}

