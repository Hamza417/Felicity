package app.simple.felicity.ui.main.genres

import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.transition.TransitionManager
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.page.GenreDetailsAdapter
import app.simple.felicity.databinding.FragmentViewerGenresBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.AppearancePreferences.setCornerRadius
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.models.Artist
import app.simple.felicity.repository.models.Genre
import app.simple.felicity.repository.models.Song
import app.simple.felicity.ui.main.artists.ArtistPage
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.main.genres.GenreViewerViewModel
import com.google.android.material.transition.MaterialContainerTransform
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback

@AndroidEntryPoint
class GenrePage : MediaFragment() {

    private lateinit var binding: FragmentViewerGenresBinding

    private val genre: Genre by lazy {
        requireArguments().parcelable(BundleConstants.GENRE)
            ?: throw IllegalArgumentException("Genre is required")
    }

    private val genreViewerViewModel by viewModels<GenreViewerViewModel>(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<GenreViewerViewModel.Factory> {
                    it.create(genre = genre)
                }
            }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentViewerGenresBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        genreViewerViewModel.getData().observe(viewLifecycleOwner) { data ->
            Log.i(TAG, "onViewCreated: Received songs for genre: ${genre.name}, count: ${data.songs}")
            val adapter = GenreDetailsAdapter(data, genre)
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(48, true))
            binding.recyclerView.adapter = adapter

            adapter.setGenreSongsAdapterListener(object : GenreDetailsAdapter.Companion.GenreSongsAdapterListener {
                override fun onSongClick(songs: List<Song>, position: Int, view: View) {
                    Log.i(TAG, "onSongClick: Song clicked in genre: ${genre.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onPlayClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onPlayClick: Play button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs, position)
                }

                override fun onShuffleClick(songs: List<Song>, position: Int) {
                    Log.i(TAG, "onShuffleClick: Shuffle button clicked for genre: ${genre.name}, position: $position")
                    setMediaItems(songs.shuffled(), position)
                }

                override fun onArtistClicked(artist: Artist) {
                    Log.i(TAG, "onArtistClicked: Artist clicked in genre: ${genre.name}, artist: ${artist.name}")
                    openFragment(ArtistPage.newInstance(artist), ArtistPage.TAG)
                }

                override fun onMenuClicked(view: View) {
                    Log.i(TAG, "onMenuClicked: Menu clicked in genre: ${genre.name}")
                    showSharedElementPopup(requireActivity().findViewById(R.id.app_container), view, listOf("Option 1", "Option 2", "Option 3")) { option ->
                        Log.i(TAG, "Selected option: $option")
                        // Handle option click
                    }
                }
            })
        }
    }

    fun showSharedElementPopup(
            container: ViewGroup,
            anchorView: View,
            options: List<String>,
            onOptionClick: (String) -> Unit
    ) {
        val rootView = container

        val transitionName = "popup_shared_element"
        ViewCompat.setTransitionName(anchorView, transitionName)

        val scrimView = View(anchorView.context).apply {
            setBackgroundColor(Color.parseColor("#80000000")) // semi-transparent black
            layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true // intercept clicks to dismiss
        }

        // Add scrim to root view
        rootView.addView(scrimView)

        // Create popup container centered on screen, no margins based on anchor location
        val popupContainer = FrameLayout(anchorView.context).apply {
            setBackgroundColor(Color.WHITE)
            elevation = 24f
            ViewCompat.setTransitionName(this, transitionName)
            background = ShapeDrawable().apply {
                paint.color = Color.WHITE
                paint.isAntiAlias = true
                setCornerRadius(24f)
            }
            // Optional: set a fixed size or wrap content, centered later
            layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                // No margins to force top position
            }
        }

        // Add options as before (stacked TextViews)...

        // Add popup but keep it initially invisible (so we can morph it in)
        popupContainer.visibility = View.INVISIBLE
        rootView.addView(popupContainer)
        rootView.invalidate()

        // Hide the anchorView during popup visibility
        anchorView.visibility = View.INVISIBLE

        // MaterialContainerTransform morph from anchor to popup
        val transform = MaterialContainerTransform().apply {
            startView = anchorView
            endView = popupContainer
            addTarget(popupContainer)
            duration = 350
            scrimColor = Color.argb(100, 0, 0, 0)
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
            containerColor = Color.WHITE
            startElevation = anchorView.elevation
            endElevation = 24f
            fitMode = MaterialContainerTransform.FIT_MODE_AUTO
        }

        rootView.post {
            // Make popup visible before animation
            popupContainer.visibility = View.VISIBLE

            TransitionManager.beginDelayedTransition(rootView, transform)
        }

        // Set up dismiss and reverse morph logic
        fun dismissPopup() {
            val reverseTransform = MaterialContainerTransform().apply {
                startView = popupContainer
                endView = anchorView
                addTarget(popupContainer)
                duration = 350
                scrimColor = Color.TRANSPARENT
                fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
                containerColor = Color.WHITE
                startElevation = 24f
                endElevation = anchorView.elevation
                fitMode = MaterialContainerTransform.FIT_MODE_AUTO
            }
            TransitionManager.beginDelayedTransition(rootView, reverseTransform)

            // Swap visibility states at end of animation using a listener
            reverseTransform.addListener(object : androidx.transition.Transition.TransitionListener {
                override fun onTransitionStart(transition: androidx.transition.Transition) {}
                override fun onTransitionEnd(transition: androidx.transition.Transition) {
                    rootView.removeView(popupContainer)
                    anchorView.visibility = View.VISIBLE
                    reverseTransform.removeListener(this)
                }

                override fun onTransitionCancel(transition: androidx.transition.Transition) {}
                override fun onTransitionPause(transition: androidx.transition.Transition) {}
                override fun onTransitionResume(transition: androidx.transition.Transition) {}
            })

            // Hide popup immediately to animate disappearance
            popupContainer.visibility = View.INVISIBLE
        }

        // Outside click dismiss
        popupContainer.setOnClickListener {
            dismissPopup()
        }

        popupContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                popupContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val parentHeight = rootView.height
                val popupHeight = popupContainer.height
                val topMargin = (parentHeight - popupHeight) / 2

                val params = popupContainer.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = topMargin
                params.bottomMargin = 0
                popupContainer.layoutParams = params
            }
        })

        // Add options as TextViews to the popupContainer
        options.forEach { option ->
            val optionView = TextView(anchorView.context).apply {
                text = option
                textSize = 18f
                setPadding(48, 32, 48, 32)
                setTextColor(Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }
            popupContainer.addView(optionView)
        }

        // Option clicks dismiss and callback
        options.forEachIndexed { idx, option ->
            val optionView = popupContainer.getChildAt(idx) as TextView
            optionView.setOnClickListener {
                onOptionClick(option)
                rootView.removeView(scrimView)
                dismissPopup()
            }
        }

        scrimView.setOnClickListener {
            // Dismiss on scrim click
            rootView.removeView(scrimView)
            dismissPopup()
        }
    }

    companion object {
        fun newInstance(genre: Genre): GenrePage {
            val args = Bundle()
            args.putParcelable(BundleConstants.GENRE, genre)
            val fragment = GenrePage()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "GenreSongs"
    }
}