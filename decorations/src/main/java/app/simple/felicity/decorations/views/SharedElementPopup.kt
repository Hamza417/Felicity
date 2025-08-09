package app.simple.felicity.decorations.views

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.setPadding
import androidx.transition.TransitionManager
import app.simple.felicity.decoration.R
import com.google.android.material.transition.MaterialContainerTransform
import kotlin.math.roundToLong

/**
 * Reusable shared element popup with morph animation.
 *
 * Inflate custom content layout inside this popup.
 * Extend this class to customize behavior or UI.
 */
open class SharedElementPopup @JvmOverloads constructor(
        private val container: ViewGroup,
        private val anchorView: View,
        private val layoutResId: Int,
        private val onPopupInflated: (View) -> Unit = {},
        private val onDismiss: (() -> Unit)? = null
) {

    private lateinit var scrimView: View
    private lateinit var popupContainer: FrameLayout

    companion object {
        private const val TRANSITION_NAME = "shared_element_popup_transition"
        private const val POPUP_WIDTH = 0.75F
        private const val DURATION = 350L
        private const val END_ELEVATION = 0f
        private val INTERPOLATOR = DecelerateInterpolator(1.5F)
    }

    fun show() {
        val customView = LayoutInflater.from(container.context)
            .inflate(layoutResId, null, false)

        // Setup scrim
        scrimView = View(container.context).apply {
            setBackgroundColor("#80000000".toColorInt())
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setOnClickListener { dismiss() }
            alpha = 0f
            animate().alpha(1f).setDuration(200).start()
        }

        container.addView(scrimView)

        // Popup container
        popupContainer = FrameLayout(container.context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            background = null
            elevation = END_ELEVATION
            ViewCompat.setTransitionName(this, TRANSITION_NAME)
            layoutParams = CoordinatorLayout.LayoutParams(
                    (container.width * POPUP_WIDTH).toInt(),
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            setPadding(resources.getDimensionPixelSize(R.dimen.padding_15))
            clipChildren = false
            clipToPadding = false

            visibility = View.INVISIBLE
            addView(customView)
        }

        container.addView(popupContainer)

        // Animate morph
        ViewCompat.setTransitionName(anchorView, TRANSITION_NAME)
        anchorView.visibility = View.INVISIBLE

        val transform = MaterialContainerTransform().apply {
            startView = anchorView
            endView = popupContainer
            addTarget(popupContainer)
            duration = DURATION
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            interpolator = INTERPOLATOR
        }

        popupContainer.post {
            popupContainer.visibility = View.VISIBLE
            TransitionManager.beginDelayedTransition(container, transform)
        }

        onPopupInflated(customView)
    }

    fun dismiss() {
        val reverseTransform = MaterialContainerTransform().apply {
            startView = popupContainer
            endView = anchorView
            addTarget(popupContainer)
            duration = DURATION
            scrimColor = Color.TRANSPARENT
            containerColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            startElevation = END_ELEVATION
            endElevation = END_ELEVATION
            interpolator = INTERPOLATOR
        }

        reverseTransform.addListener(object : androidx.transition.Transition.TransitionListener {
            override fun onTransitionEnd(transition: androidx.transition.Transition) {
                anchorView.visibility = View.VISIBLE
                container.removeView(popupContainer)
                scrimView.clearAnimation()
                container.removeView(scrimView)
                onDismiss?.invoke()
                reverseTransform.removeListener(this)
            }

            override fun onTransitionStart(t: androidx.transition.Transition) {
                scrimView.alpha = 1f
                scrimView.animate()
                    .alpha(0f)
                    .setDuration(DURATION.div(1.5F).roundToLong())
                    .start()
            }

            override fun onTransitionCancel(t: androidx.transition.Transition) {}
            override fun onTransitionPause(t: androidx.transition.Transition) {}
            override fun onTransitionResume(t: androidx.transition.Transition) {}
        })

        TransitionManager.beginDelayedTransition(container, reverseTransform)
        popupContainer.visibility = View.INVISIBLE
    }
}

