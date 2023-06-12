package app.simple.felicity.ui.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.R
import app.simple.felicity.adapters.artflow.ArtFlowRvAdapter
import app.simple.felicity.constants.BundleConstants
import app.simple.felicity.databinding.FragmentArtflowRvBinding
import app.simple.felicity.decorations.helpers.GravitySnapHelper
import app.simple.felicity.decorations.itemdecorations.BoundsOffsetDecoration
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.layoutmanager.ProminentLayoutManager
import app.simple.felicity.utils.ConditionUtils.isNotNull
import app.simple.felicity.viewmodels.ui.SongsViewModel
import app.simple.inure.extensions.fragments.ScopedFragment

class ArtFlowRv : ScopedFragment() {

    private lateinit var snapHelper: GravitySnapHelper
    private var layoutManager: ProminentLayoutManager? = null
    private lateinit var songsViewModel: SongsViewModel
    private lateinit var binding: FragmentArtflowRvBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_artflow_rv, container, false)

        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]
        binding = FragmentArtflowRvBinding.bind(view)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startPostponedEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            layoutManager = ProminentLayoutManager(requireContext(), 2.5F, 0.8F)
            snapHelper = GravitySnapHelper(Gravity.CENTER) { position ->
                requireArguments().putInt(BundleConstants.position, position)
                binding.title.text = it[position].title
                binding.artist.text = it[position].artist
                Log.d("ArtFlow", "Scrolled to position: $position")
            }

            with(binding.artFlow) {
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                adapter = ArtFlowRvAdapter(it)
                layoutManager = this@ArtFlowRv.layoutManager

                val spacing = resources.getDimensionPixelSize(R.dimen.carousel_spacing)
                addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
                addItemDecoration(BoundsOffsetDecoration())

                snapHelper.attachToRecyclerView(this)

                if (savedInstanceState.isNotNull()) {
                    initRecyclerViewPosition(requireArguments().getInt(BundleConstants.position))
                }
            }
        }
    }

    private fun initRecyclerViewPosition(position: Int) {
        // This initial scroll will be slightly off because it doesn't
        // respect the SnapHelper. Do it anyway so that the target view
        // is laid out, then adjust onPreDraw.
        snapHelper.attachToRecyclerView(binding.artFlow)
        layoutManager?.scrollToPosition(position)

        binding.artFlow.doOnPreDraw {
            val targetView = layoutManager?.findViewByPosition(position) ?: return@doOnPreDraw
            val distanceToFinalSnap = snapHelper.calculateDistanceToFinalSnap(layoutManager!!, targetView)
            layoutManager!!.scrollToPositionWithOffset(position, -distanceToFinalSnap[0])
        }
    }

    companion object {
        fun newInstance(): ArtFlowRv {
            val args = Bundle()
            val fragment = ArtFlowRv()
            fragment.arguments = args
            return fragment
        }
    }
}