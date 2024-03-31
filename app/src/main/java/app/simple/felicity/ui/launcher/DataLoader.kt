package app.simple.felicity.ui.launcher

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.loader.AdapterLoader
import app.simple.felicity.databinding.FragmentLoaderBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.data.DataLoaderViewModel

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

        adapterLoader = AdapterLoader()
        (binding?.recyclerView?.layoutManager as LinearLayoutManager).reverseLayout = true
        (binding?.recyclerView?.layoutManager as LinearLayoutManager).stackFromEnd = true
        binding?.recyclerView?.itemAnimator = null
        binding?.recyclerView?.adapter = adapterLoader

        dataLoaderViewModel?.getData()?.observe(viewLifecycleOwner) { file ->
            adapterLoader?.updateFile(file)
            binding?.recyclerView?.smoothScrollToPosition(0)
        }

        dataLoaderViewModel?.getLoaded()?.observe(viewLifecycleOwner) { loaded ->
            if (loaded) {
                binding?.loading?.setText(R.string.done)
                binding?.loader?.loaded()
            }
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
