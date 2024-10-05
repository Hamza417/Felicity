package app.simple.felicity.ui.main.songs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.SharedElementCallback
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.adapters.ui.lists.songs.SimpleSongsAdapter
import app.simple.felicity.databinding.FragmentSongsBinding
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.shared.utils.ConditionUtils.invert
import app.simple.felicity.ui.player.DefaultPlayer
import app.simple.felicity.viewmodels.main.songs.SongsViewModel

class SimpleSongs : ScopedFragment() {

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

        songsViewModel.getSongs().observe(viewLifecycleOwner) {
            binding.recyclerView.adapter = SimpleSongsAdapter(it)
            Log.i("Music", "Songs: ${it.size}")

            (binding.recyclerView.adapter as SimpleSongsAdapter).onItemClickListener = { _, position, view ->
                app.simple.felicity.preferences.MusicPreferences.setMusicPosition(position)

                openFragmentArc(DefaultPlayer.newInstance(), view, "audio_player_pager")
                requireArguments().putInt(app.simple.felicity.shared.constants.BundleConstants.position, position)
            }

            binding.recyclerView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    if (requireArguments().getBoolean(app.simple.felicity.shared.constants.BundleConstants.firstLaunch, true).invert()) { // Make sure first launch doesn't jump to position
                        binding.recyclerView.removeOnLayoutChangeListener(this)
                        val layoutManager = binding.recyclerView.layoutManager
                        val viewAtPosition = layoutManager!!.findViewByPosition(app.simple.felicity.preferences.MusicPreferences.getMusicPosition())

                        /**
                         * Scroll to position if the view for the current position is null
                         * (not currently part of layout manager children), or it's not completely
                         * visible.
                         */
                        if (viewAtPosition == null || layoutManager.isViewPartiallyVisible(viewAtPosition, false, true)) {
                            binding.recyclerView.post {
                                // Log.d("Music", displayHeight.toString())
                                (binding.recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(app.simple.felicity.preferences.MusicPreferences.getMusicPosition(), 400)

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
                        requireArguments().putBoolean(app.simple.felicity.shared.constants.BundleConstants.firstLaunch, false)
                    }
                }
            })

            if (requireArguments().getInt(app.simple.felicity.shared.constants.BundleConstants.position, app.simple.felicity.preferences.MusicPreferences.getMusicPosition()) == app.simple.felicity.preferences.MusicPreferences.getMusicPosition()) {
                (view.parent as? ViewGroup)?.doOnPreDraw {
                    startPostponedEnterTransition()
                }
            }
        }

        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
                // Locate the ViewHolder for the clicked position.
                val selectedViewHolder = binding.recyclerView.findViewHolderForAdapterPosition(app.simple.felicity.preferences.MusicPreferences.getMusicPosition().plus(1))
                if (selectedViewHolder is SimpleSongsAdapter.Holder) {
                    // Map the first shared element name to the child ImageView.
                    sharedElements[names[0]] = selectedViewHolder.binding.albumArt
                }
            }
        })
    }

    companion object {
        fun newInstance(): SimpleSongs {
            val args = Bundle()
            val fragment = SimpleSongs()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "Songs"
    }
}
