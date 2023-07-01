package app.simple.felicity.ui.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.SharedElementCallback
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.SongsAdapter
import app.simple.felicity.constants.BundleConstants
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.loaders.MediaLoader
import app.simple.felicity.preferences.MusicPreferences
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.utils.ConditionUtils.invert
import app.simple.felicity.viewmodels.ui.SongsViewModel

class Songs : ScopedFragment() {

    private lateinit var binding: FragmentSongsBinding
    private lateinit var songsViewModel: SongsViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_songs, container, false)

        binding = FragmentSongsBinding.bind(view)
        songsViewModel = ViewModelProvider(requireActivity())[SongsViewModel::class.java]

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            binding.recyclerView.adapter = SongsAdapter(it)

            (binding.recyclerView.adapter as SongsAdapter).onItemClickListener = { _, position, view ->
                MusicPreferences.setMusicPosition(position)
                MusicPreferences.setMediaMusicCategory(MediaLoader.MEDIA_ID_SONGS)
                openFragmentArc(DefaultPlayer.newInstance(), view, "audio_player_pager")
                requireArguments().putInt(BundleConstants.position, position)
            }

            binding.recyclerView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    if (requireArguments().getBoolean(BundleConstants.firstLaunch, true).invert()) { // Make sure first launch doesn't jump to position
                        binding.recyclerView.removeOnLayoutChangeListener(this)
                        val layoutManager = binding.recyclerView.layoutManager
                        val viewAtPosition = layoutManager!!.findViewByPosition(MusicPreferences.getMusicPosition())

                        /**
                         * Scroll to position if the view for the current position is null
                         * (not currently part of layout manager children), or it's not completely
                         * visible.
                         */
                        if (viewAtPosition == null || layoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                            binding.recyclerView.post {
                                // Log.d("Music", displayHeight.toString())
                                (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(MusicPreferences.getMusicPosition(), 400)

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

        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                // Locate the ViewHolder for the clicked position.
                val selectedViewHolder = binding.recyclerView.findViewHolderForAdapterPosition(MusicPreferences.getMusicPosition().plus(1))
                if (selectedViewHolder is SongsAdapter.Holder) {
                    // Map the first shared element name to the child ImageView.
                    sharedElements[names[0]] = selectedViewHolder.binding.albumArt
                }
            }
        })
    }

    companion object {
        fun newInstance(): Songs {
            val args = Bundle()
            val fragment = Songs()
            fragment.arguments = args
            return fragment
        }
    }
}