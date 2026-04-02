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
 * A seekable transition that animates fragments with a pure crossfade effect.
 * <p>
 * No translation is applied — the entering fragment fades in while the exiting
 * fragment fades out, both occupying the same position on screen simultaneously.
 * <p>
 * Supports predictive back gestures for smooth, responsive navigation.
 */
public class SeekableSharedAxisFadeTransition extends BaseSeekableTransition {

    public SeekableSharedAxisFadeTransition(boolean forward) {
        super(forward);
    }

    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * Entering fragment fades in from fully transparent to fully opaque.
         * The direction flag is not used here — a crossfade has no inherent direction.
         */
        return createAnimator(view, 0f, 1f);
    }

    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        /*
         * Exiting fragment fades out from fully opaque to fully transparent.
         */
        return createAnimator(view, 1f, 0f);
    }

    private Animator createAnimator(final View view,
            final float startAlpha,
            final float endAlpha) {
        ArtFlow artFlow = findCoverFlow(view);

        ValueAnimator animator = createBaseAnimator();

        animator.addUpdateListener(animation -> {
            float progress = getProgress(animation);
            float alpha = startAlpha + (endAlpha - startAlpha) * progress;
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
                float finalAlpha = p >= 0.5f ? endAlpha : startAlpha;
                view.setAlpha(finalAlpha);
                if (artFlow != null) {
                    artFlow.setAlpha(finalAlpha);
                }
                resetControlFlag();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
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

