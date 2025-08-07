package app.simple.felicity.decorations.views

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialContainerTransform

/**
 * Reusable shared element popup with morph animation.
 *
 * Inflate custom content layout inside this popup.
 * Extend this class to customize behavior or UI.
 */
open class SharedElementPopup @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private lateinit var scrimView: View
    private lateinit var popupContainer: FrameLayout

    private var onDismissListener: (() -> Unit)? = null

    companion object {
        private const val TRANSITION_NAME = "shared_element_popup_transition"
        private const val POPUP_WIDTH = 0.75F
    }

    /**
     * Show popup inflating the given layout resource.
     */
    fun show(
            container: ViewGroup,
            sharedView: View,
            layoutResId: Int,
            onPopupInflated: (View) -> Unit = {},
            onDismiss: (() -> Unit)? = null
    ) {
        val inflatedView = LayoutInflater.from(context).inflate(layoutResId, null, false)
        show(container, sharedView, inflatedView, onDismiss)
        onPopupInflated(inflatedView)
    }

    /**
     * Show popup using the provided custom inflated View.
     */
    fun show(
            container: ViewGroup,
            sharedView: View,
            customView: View,
            onDismiss: (() -> Unit)? = null
    ) {
        // Setup scrim view
        scrimView = View(context).apply {
            setBackgroundColor(Color.parseColor("#80000000")) // Dim background
            layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true // intercept taps
            setOnClickListener {
                dismiss()
            }
        }
        container.addView(scrimView)

        // Setup popup container FrameLayout
        popupContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 24f
            ViewCompat.setTransitionName(this, TRANSITION_NAME)
            background = ShapeDrawable().apply {
                paint.color = Color.WHITE
                paint.isAntiAlias = true
            }
            // Set popup size to 80% width by default, height wrap_content
            layoutParams = LayoutParams((container.width * POPUP_WIDTH).toInt(), LayoutParams.WRAP_CONTENT)
            visibility = INVISIBLE
            addView(customView)  // Add your custom inflated view here
        }
        container.addView(popupContainer)

        // Position popup vertically centered after layout
        popupContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                popupContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val parentHeight = container.height
                val popupHeight = popupContainer.height
                val topMargin = (parentHeight - popupHeight) / 2
                val params = popupContainer.layoutParams as LayoutParams
                params.topMargin = topMargin
                popupContainer.layoutParams = params
            }
        })

        // Set transition names and hide anchor view
        ViewCompat.setTransitionName(sharedView, TRANSITION_NAME)
        sharedView.visibility = View.INVISIBLE

        // Start the morph animation
        val transform = MaterialContainerTransform().apply {
            startView = sharedView
            endView = popupContainer
            addTarget(popupContainer)
            duration = 350L
            scrimColor = Color.argb(100, 0, 0, 0)
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            containerColor = Color.WHITE
            startElevation = sharedView.elevation
            endElevation = 24f
            fitMode = MaterialContainerTransform.FIT_MODE_AUTO
        }

        popupContainer.post {
            popupContainer.visibility = VISIBLE
            TransitionManager.beginDelayedTransition(container, transform)
        }

        // Setup dismiss listener
        onDismissListener = {
            sharedView.visibility = VISIBLE
            container.removeView(popupContainer)
            container.removeView(scrimView)
            onDismiss?.invoke()
        }
    }

    fun dismiss() {
        val parent = parent as? ViewGroup ?: return

        val anchorView = parent.findViewWithTag(TRANSITION_NAME)
            ?: parent.findViewWithTag<View>(TRANSITION_NAME)

        val reverseTransform = MaterialContainerTransform().apply {
            startView = popupContainer
            endView = anchorView ?: return
            addTarget(popupContainer)
            duration = 350L
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            containerColor = Color.WHITE
            startElevation = 24f
            endElevation = anchorView.elevation
            fitMode = MaterialContainerTransform.FIT_MODE_AUTO
        }

        TransitionManager.beginDelayedTransition(parent, reverseTransform)

        reverseTransform.addListener(object : androidx.transition.Transition.TransitionListener {
            override fun onTransitionStart(transition: androidx.transition.Transition) {}
            override fun onTransitionEnd(transition: androidx.transition.Transition) {
                onDismissListener?.invoke()
                reverseTransform.removeListener(this)
            }

            override fun onTransitionCancel(transition: androidx.transition.Transition) {}
            override fun onTransitionPause(transition: androidx.transition.Transition) {}
            override fun onTransitionResume(transition: androidx.transition.Transition) {}
        })

        popupContainer.visibility = INVISIBLE
    }
}
