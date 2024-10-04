package app.simple.felicity.ui.app

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import app.simple.felicity.R
import app.simple.felicity.adapters.artflow.ArtFlowRvAdapter
import app.simple.felicity.databinding.FragmentArtflowRvBinding
import app.simple.felicity.decorations.itemdecorations.BoundsOffsetDecoration
import app.simple.felicity.decorations.itemdecorations.LinearHorizontalSpacingDecoration
import app.simple.felicity.decorations.layoutmanager.ProminentLayoutManager
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.models.normal.Audio
import app.simple.felicity.shared.utils.ConditionUtils.isNotNull
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class ArtFlowRv : ScopedFragment() {

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

            with(binding.artFlow) {
                // setItemViewCacheSize(20)
                backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                adapter = ArtFlowRvAdapter(it)
                layoutManager = this@ArtFlowRv.layoutManager

                val spacing = resources.getDimensionPixelSize(R.dimen.carousel_spacing)
                addItemDecoration(LinearHorizontalSpacingDecoration(spacing))
                addItemDecoration(BoundsOffsetDecoration())

                setSnapListener { position ->
                    updateInfo(position, it[position])
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            binding.title.visibility = View.VISIBLE
                            binding.artist.visibility = View.VISIBLE
                        } else {
                            binding.title.visibility = View.INVISIBLE
                            binding.artist.visibility = View.INVISIBLE
                        }
                    }
                })

                snapHelper.attachToRecyclerView(this)

                if (savedInstanceState.isNotNull()) {
                    initRecyclerViewPosition(requireArguments().getInt(app.simple.felicity.shared.constants.BundleConstants.position))
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            snapHelper.invalidateSnapPosition() // Notify snap listeners about new position
                        }
                    }
                })
            }
        }

        binding.arrowLeft.setOnClickListener {
            try {
                binding.artFlow.smoothScrollToPosition(binding.artFlow.currentSnappedPosition - 10)
            } catch (e: IllegalArgumentException) {
                binding.artFlow.smoothScrollToPosition(0)
            }
        }

        binding.arrowRight.setOnClickListener {
            try {
                binding.artFlow.smoothScrollToPosition(binding.artFlow.currentSnappedPosition + 10)
            } catch (e: IllegalArgumentException) {
                binding.artFlow.smoothScrollToPosition(0)
            }
        }
    }

    private fun initRecyclerViewPosition(position: Int) {
        // This initial scroll will be slightly off because it doesn't
        // respect the SnapHelper. Do it anyway so that the target view
        // is laid out, then adjust onPreDraw.
        layoutManager?.scrollToPosition(position)

        binding.artFlow.doOnPreDraw {
            binding.artFlow.snapToPosition(position)
        }
    }

    private fun updateInfo(position: Int, audio: Audio) {
        requireArguments().putInt(app.simple.felicity.shared.constants.BundleConstants.position, position)
        binding.title.text = audio.title
        binding.artist.text = audio.artist
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
