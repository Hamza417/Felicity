package app.simple.felicity.ui.launcher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.core.utils.BitmapHelper.addLinearGradient
import app.simple.felicity.core.utils.BitmapHelper.toBitmapKeepingSize
import app.simple.felicity.databinding.FragmentSplashScreenBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.extensions.viewmodels.DatabaseLoaderViewModel
import app.simple.felicity.ui.main.home.SimpleListHome

@SuppressLint("CustomSplashScreen")
class SplashScreen : ScopedFragment() {

    private val databaseLoaderViewModels: DatabaseLoaderViewModel by viewModels()
    private var binding: FragmentSplashScreenBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentSplashScreenBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        parentFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
            .replace(R.id.app_container, SimpleListHome.newInstance())
            .commitAllowingStateLoss()

        binding?.appIcon?.setImageBitmap(R.drawable.ic_felicity.toBitmapKeepingSize(requireContext(), 10)
                                             .addLinearGradient(intArrayOf(app.simple.felicity.theme.managers.ThemeManager.accent.primaryAccentColor, app.simple.felicity.theme.managers.ThemeManager.accent.secondaryAccentColor)))
    }

    companion object {
        fun newInstance(): SplashScreen {
            val args = Bundle()
            val fragment = SplashScreen()
            fragment.arguments = args
            return fragment
        }
    }
}
