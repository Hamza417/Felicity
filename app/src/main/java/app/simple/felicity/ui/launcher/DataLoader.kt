package app.simple.felicity.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.adapters.loader.AdapterLoader
import app.simple.felicity.core.utils.ViewUtils.visible
import app.simple.felicity.databinding.FragmentLoaderBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.preferences.MainPreferences
import app.simple.felicity.ui.main.home.SimpleListHome
import app.simple.felicity.viewmodels.data.DataLoaderViewModel
import kotlinx.coroutines.launch

class DataLoader : ScopedFragment() {

    private var binding: FragmentLoaderBinding? = null
    private var dataLoaderViewModel: DataLoaderViewModel? = null
    private var adapterLoader: AdapterLoader? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLoaderBinding.inflate(inflater, container, false)

        dataLoaderViewModel = ViewModelProvider(this)[DataLoaderViewModel::class.java]

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        binding?.openAppNow?.isClickable = false
        dataLoaderViewModel?.startSyncWorker()

        lifecycleScope.launch {
            dataLoaderViewModel?.isCompleted?.collect {
                if (it) {
                    binding?.loading?.setText(R.string.done)
                    binding?.loader?.loaded()
                    binding?.openAppNow?.isClickable = true
                    binding?.openAppNow?.visible(true)
                    MainPreferences.setDataLoaded(true)
                }
            }
        }

        binding?.openAppNow?.setOnClickListener {
            openFragmentSlide(SimpleListHome.newInstance())
        }
    }

    companion object {
        fun newInstance(): DataLoader {
            val args = Bundle()
            val fragment = DataLoader()
            fragment.arguments = args
            return fragment
        }
    }
}
