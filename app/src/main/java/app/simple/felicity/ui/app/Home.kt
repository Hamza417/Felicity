package app.simple.felicity.ui.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.HomeAdapter
import app.simple.felicity.databinding.FragmentHomeBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.viewmodels.ui.HomeViewModel

class Home : ScopedFragment() {

    private var binding: FragmentHomeBinding? = null
    private var homeViewModel: HomeViewModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()
        binding?.recyclerView?.setHasFixedSize(true)
        binding?.recyclerView?.backgroundTintList = ColorStateList(
                arrayOf(intArrayOf()),
                intArrayOf(Color.BLACK))

        homeViewModel?.getHomeData()?.observe(viewLifecycleOwner) {
            binding?.recyclerView?.adapter = HomeAdapter(it)

            (binding?.recyclerView?.adapter as HomeAdapter).onContainerClicked = { view, position ->
                when (it[position].first) {
                    R.string.songs -> {
                        openFragmentSlide(Songs.newInstance(), "songs")
                    }
                }
            }

            binding?.recyclerView?.scheduleLayoutAnimation()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        fun newInstance(): Home {
            val args = Bundle()
            val fragment = Home()
            fragment.arguments = args
            return fragment
        }
    }
}