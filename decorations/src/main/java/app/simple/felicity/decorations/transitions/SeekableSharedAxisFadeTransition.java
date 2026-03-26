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
         * Restore full opacity when the animation finishes or is cancelled
         * so subsequent renders are not affected by a leftover alpha value.
         */
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setAlpha(1f);
                resetControlFlag();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                view.setAlpha(1f);
                resetControlFlag();
            }
        });

        return animator;
    }
}

