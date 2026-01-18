package app.simple.felicity.extensions.dialogs

import android.app.Application
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.simple.felicity.R
import app.simple.felicity.activities.MainActivity
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.manager.SharedPreferences.registerSharedPreferenceChangeListener
import app.simple.felicity.manager.SharedPreferences.unregisterSharedPreferenceChangeListener
import app.simple.felicity.shared.utils.ViewUtils
import com.google.android.material.R.id.design_bottom_sheet
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

abstract class ScopedBottomSheetFragment : BottomSheetDialogFragment(),
                                           SharedPreferences.OnSharedPreferenceChangeListener {

    open val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.CustomBottomSheetDialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        dialog?.window?.attributes?.windowAnimations = R.style.BottomDialogAnimation

        dialog?.window?.setDimAmount(ViewUtils.dimAmount)

        dialog?.setOnShowListener { dialog ->
            /**
             * In a previous life I used this method to get handles to the positive and negative buttons
             * of a dialog in order to change their Typeface. Good ol' days.
             */
            val sheetDialog = dialog as BottomSheetDialog

            /**
             * This is gotten directly from the source of BottomSheetDialog
             * in the wrapInBottomSheet() method
             */
            val bottomSheet = sheetDialog.findViewById<View>(design_bottom_sheet) as FrameLayout

            /**
             *  Right here!
             *  Make sure the dialog pops up being fully expanded
             */
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED

            /**
             * Also make sure the dialog doesn't half close when we don't want
             * it to be, so we close them
             */
            BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HALF_EXPANDED) {
                        dismiss()
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // do nothing
                }
            })
        }
    }

    protected fun requireHiddenMiniPlayer() {
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                (requireActivity() as MainActivity).onHideMiniPlayer()
            }

            override fun onPause(owner: LifecycleOwner) {
                super.onPause(owner)
                // Don't force-show during configuration changes; preserve current state
                if (requireActivity().isChangingConfigurations.not()) {
                    (requireActivity() as MainActivity).onShowMiniPlayer()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        registerSharedPreferenceChangeListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterSharedPreferenceChangeListener()
    }

    /**
     * Called when any preferences is changed using [app.simple.felicity.manager.SharedPreferences.getSharedPreferences]
     *
     * Override this to get any preferences change events inside
     * the fragment
     */
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {}

    /**
     * Return the {@link Application} this fragment is currently associated with.
     */
    @Suppress("unused")
    protected fun requireApplication(): Application {
        return requireActivity().application
    }

    protected fun openAppSettings() {
        try {
            (parentFragment as ScopedFragment).openPreferencesPanel()
        } catch (e: Exception) {
            Log.e("ScopedBottomSheetFragment", "openAppSettings: ${e.message}")
        }
    }
}
