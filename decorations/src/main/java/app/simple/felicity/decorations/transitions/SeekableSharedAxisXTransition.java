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
 * A seekable transition that animates fragments with a shared axis X effect.
 * <p>
 * Unlike a full pager-style slide, this transition moves each fragment only a fraction
 * of the screen width while simultaneously fading in/out, creating an overlapping
 * crossfade-with-drift feel. Both the entering and exiting fragments are visible at
 * the same time during the animation.
 * <p>
 * Supports predictive back gestures for smooth, responsive navigation.
 */
public class SeekableSharedAxisXTransition extends BaseSeekableTransition {

    /**
     * The fraction of the scene root width used as the translation distance.
     * 0.25 means each fragment drifts 25% of the screen width — enough to convey
     * direction without becoming a full slide.
     */
    private static final float TRANSLATION_FRACTION = 0.25f;

    public SeekableSharedAxisXTransition(boolean forward) {
        super(forward);
    }

    @Override
    public Animator onAppear(@NonNull ViewGroup sceneRoot,
            @NonNull View view,
            TransitionValues startValues,
            TransitionValues endValues) {
        /*
         * Entering fragment drifts in from the side and fades in.
         * Going forward: enter from the right (+offset).
         * Going back: enter from the left (-offset).
         */
        float distance = sceneRoot.getWidth() * TRANSLATION_FRACTION;
        float startTranslationX = forward ? distance : -distance;
        return createAnimator(view, startTranslationX, 0f, 0f, 1f);
    }

    @Override
    public Animator onDisappear(@NonNull ViewGroup sceneRoot, @NonNull View view,
            TransitionValues startValues, TransitionValues endValues) {
        /*
         * Exiting fragment drifts out to the side and fades out.
         * Going forward: exit to the left (-offset).
         * Going back: exit to the right (+offset).
         */
        float distance = sceneRoot.getWidth() * TRANSLATION_FRACTION;
        float endTranslationX = forward ? -distance : distance;
        return createAnimator(view, 0f, endTranslationX, 1f, 0f);
    }

    private Animator createAnimator(final View view,
            final float startTranslationX,
            final float endTranslationX,
            final float startAlpha,
            final float endAlpha) {
        ArtFlow artFlow = findCoverFlow(view);

        ValueAnimator animator = createBaseAnimator();

        animator.addUpdateListener(animation -> {
            /*
             * Apply progress to both translation and alpha so the two fragments
             * overlap and crossfade while drifting along the X axis.
             */
            float progress = getProgress(animation);

            float translation = startTranslationX + (endTranslationX - startTranslationX) * progress;
            float alpha = startAlpha + (endAlpha - startAlpha) * progress;
            view.setTranslationX(translation);
            view.setAlpha(alpha);

            if (artFlow != null) {
                artFlow.setAlpha(alpha);
            }
        });

        /*
         * Reset view state when the animation completes or is cancelled so
         * nothing is left in a partial-translation / partial-alpha state.
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

