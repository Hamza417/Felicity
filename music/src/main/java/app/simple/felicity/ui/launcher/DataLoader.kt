package app.simple.felicity.ui.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.databinding.FragmentLoaderBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.repository.SynchronizerService
import app.simple.felicity.ui.main.home.SimpleListHome
import kotlinx.coroutines.launch

class DataLoader : ScopedFragment() {

    private var binding: FragmentLoaderBinding? = null
    private var serviceConnection: ServiceConnection? = null
    private var synchronizerService: SynchronizerService? = null

    private var isServiceBound = false // Shouldn't I just set synchronizerService to null? idk

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isServiceBound = true
                synchronizerService = (service as SynchronizerService.SynchronizerBinder).getService()

                viewLifecycleOwner.lifecycleScope.launch {
                    service.getTimeRemaining().let { flow ->
                        flow.collect {
                            binding?.count?.text = it.second
                        }
                    }
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    service.isCompleted().let { flow ->
                        flow.collect {
                            if (it) {
                                binding?.next?.visibility = View.VISIBLE
                                binding?.loader?.loaded()
                            }
                        }
                    }
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    service.getCurrentFileName().let { flow ->
                        flow.collect {
                            binding?.data?.text = it
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isServiceBound = false
                synchronizerService = null
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentLoaderBinding.inflate(inflater, container, false)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        binding?.next?.setOnClickListener {
            openFragmentSlide(SimpleListHome.newInstance())
        }
    }

    override fun onStart() {
        super.onStart()
        startService()
    }

    private fun startService() {
        val intent = SynchronizerService.getSyncServiceIntent(requireContext())
        requireActivity().startService(intent)
        serviceConnection?.let {
            requireActivity().bindService(intent, it, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        synchronizerService?.let {
            requireActivity().unbindService(serviceConnection!!)
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
