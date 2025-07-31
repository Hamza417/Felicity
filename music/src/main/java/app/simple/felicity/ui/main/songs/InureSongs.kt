package app.simple.felicity.ui.main.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.SharedElementCallback
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.adapters.ui.lists.songs.InureSongsAdapter
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.engine.managers.AudioStateManager
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.shared.constants.BundleConstants
import app.simple.felicity.viewmodels.main.songs.SongsViewModel
import kotlinx.coroutines.launch

class InureSongs : ScopedFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var songsViewModel: SongsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        binding = FragmentSongsBinding.inflate(inflater, container, false)
        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                songsViewModel.songs.collect {
                    binding.recyclerView.adapter = InureSongsAdapter(it)

                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }

                    (binding.recyclerView.adapter as InureSongsAdapter).onItemClickListener = { _, position, view ->
                        AudioStateManager.setPlaylist(it, position)
                    }

                    binding.recyclerView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                            if (requireArguments().getBoolean(BundleConstants.firstLaunch, true).not()) { // Make sure first launch doesn't jump to position
                                binding.recyclerView.removeOnLayoutChangeListener(this)
                                val layoutManager = binding.recyclerView.layoutManager
                                val viewAtPosition =
                                    layoutManager!!.findViewByPosition(MusicPreferences.getMusicPosition())

                                /**
                                 * Scroll to position if the view for the current position is null
                                 * (not currently part of layout manager children), or it's not completely
                                 * visible.
                                 */
                                if (viewAtPosition == null || layoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                                    binding.recyclerView.post {
                                        // Log.d("Music", displayHeight.toString())
                                        (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                                                MusicPreferences.getMusicPosition(),
                                                400
                                        )

                                        (view.parent as? ViewGroup)?.doOnPreDraw {
                                            startPostponedEnterTransition()
                                        }
                                    }
                                } else {
                                    (view.parent as? ViewGroup)?.doOnPreDraw {
                                        startPostponedEnterTransition()
                                    }
                                }
                            } else {
                                (view.parent as? ViewGroup)?.doOnPreDraw {
                                    startPostponedEnterTransition()
                                }

                                binding.recyclerView.removeOnLayoutChangeListener(this)
                                requireArguments().putBoolean(BundleConstants.firstLaunch, false)
                            }
                        }
                    })

                    if (requireArguments().getInt(BundleConstants.position, MusicPreferences.getMusicPosition()) == MusicPreferences.getMusicPosition()) {
                        (view.parent as? ViewGroup)?.doOnPreDraw {
                            startPostponedEnterTransition()
                        }
                    }
                }
            }
        }

        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                // Locate the ViewHolder for the clicked position.
                val selectedViewHolder = binding.recyclerView.findViewHolderForAdapterPosition(
                        MusicPreferences.getMusicPosition().plus(1)
                )
                if (selectedViewHolder is InureSongsAdapter.Holder) {
                    // Map the first shared element name to the child ImageView.
                    sharedElements[names[0]] = selectedViewHolder.binding.albumArt
                }
            }
        })
    }

    companion object {
        fun newInstance(): InureSongs {
            val args = Bundle()
            val fragment = InureSongs()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Songs"
    }
}
