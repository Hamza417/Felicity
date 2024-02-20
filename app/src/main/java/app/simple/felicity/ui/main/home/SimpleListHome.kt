package app.simple.felicity.ui.main.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.adapters.home.main.AdapterSimpleHome
import app.simple.felicity.databinding.FragmentHomeSimpleBinding
import app.simple.felicity.decorations.overscroll.CustomVerticalRecyclerView
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.ui.HomeViewModel

class SimpleListHome : ScopedFragment() {

    private lateinit var recyclerView: CustomVerticalRecyclerView

    private val homeViewModel: HomeViewModel by lazy {
        ViewModelProvider(requireActivity())[HomeViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentHomeSimpleBinding.inflate(inflater, container, false)

        recyclerView = binding.recyclerView

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        homeViewModel.getHomeData().observe(viewLifecycleOwner) { list ->
            recyclerView.adapter = AdapterSimpleHome(list)

            (view.parent as? ViewGroup)?.doOnPreDraw {
                startPostponedEnterTransition()
            }
        }
    }

    companion object {
        fun newInstance(): SimpleListHome {
            val args = Bundle()
            val fragment = SimpleListHome()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "SimpleListHome"
    }
}