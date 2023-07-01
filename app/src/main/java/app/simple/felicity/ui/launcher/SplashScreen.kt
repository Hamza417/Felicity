package app.simple.felicity.ui.launcher

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentSplashScreenBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.ui.app.ArtFlow
import app.simple.felicity.viewmodels.misc.DatabaseGeneratorViewModel

@SuppressLint("CustomSplashScreen")
class SplashScreen : ScopedFragment() {

    private val databaseGeneratorViewModels: DatabaseGeneratorViewModel by viewModels()
    private var binding: FragmentSplashScreenBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding?.root ?: FragmentSplashScreenBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        databaseGeneratorViewModels.getGeneratedData().observe(viewLifecycleOwner) {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right)
                .replace(R.id.app_container, ArtFlow.newInstance())
                .addToBackStack("home")
                .commitAllowingStateLoss()
        }
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