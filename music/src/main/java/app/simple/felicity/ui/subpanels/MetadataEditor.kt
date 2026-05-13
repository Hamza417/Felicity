package app.simple.felicity.ui.subpanels

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.R
import app.simple.felicity.databinding.FragmentMetadataEditorBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.glide.util.AudioCoverUtils.loadArtCoverWithPayload
import app.simple.felicity.repository.constants.BundleConstants
import app.simple.felicity.repository.metadata.MetadataWriter
import app.simple.felicity.repository.models.Audio
import app.simple.felicity.repository.models.MetadataSearchResult
import app.simple.felicity.utils.ParcelUtils.parcelable
import app.simple.felicity.viewmodels.panels.MetadataEditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.withCreationCallback
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Full-screen panel that allows the user to view and edit the embedded tag
 * metadata of a single audio track. Supported fields are title, artist,
 * album, album artist, year, genre, track number, total tracks, disc number,
 * compilation, composer, writer/lyricist, comment, and unsynchronized lyrics.
 *
 * Album artwork can be replaced by tapping the art thumbnail, which opens the
 * system image picker. The chosen image is copied to a temporary file and
 * passed to [app.simple.felicity.repository.metadata.MetadataWriter] for embedding.
 *
 * A "Search" button opens [MetadataSearch] which queries LrcLib and returns a
 * [MetadataSearchResult] via the Fragment Result API. All non-null fields in the
 * result are applied to the editor automatically, and any synced lyrics are saved
 * as a sidecar .lrc file alongside the audio file.
 *
 * On save, [app.simple.felicity.viewmodels.panels.MetadataEditorViewModel] writes the changes to disk via
 * TagLib, updates the Room database, and triggers a MediaStore rescan
 * so the changes are reflected in the app immediately.
 *
 * @author Hamza417
 */
@AndroidEntryPoint
class MetadataEditor : MediaFragment() {

    private lateinit var binding: FragmentMetadataEditorBinding

    private val audio: Audio by lazy {
        requireArguments().parcelable<Audio>(BundleConstants.AUDIO)!!
    }

    private val viewModel: MetadataEditorViewModel by viewModels(
            extrasProducer = {
                defaultViewModelCreationExtras.withCreationCallback<MetadataEditorViewModel.Factory> {
                    it.create(audio)
                }
            }
    )

    /** Temporary file to which the picked artwork is copied before embedding. */
    private var pendingArtworkFile: File? = null

    private val pickArtwork = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        handlePickedArtwork(uri)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = FragmentMetadataEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        populateFields()
        setupAlbumArtPicker()
        observeViewModel()
        registerMetadataSearchResult()

        binding.saveButton.setOnClickListener {
            saveMetadata()
        }

        binding.searchButton.setOnClickListener {
            openFragment(MetadataSearch.newInstance(audio), MetadataSearch.TAG)
        }
    }

    /**
     * Registers a one-time listener on [MetadataSearch.REQUEST_KEY_METADATA_RESULT] so that
     * when the user picks a result in the search screen, we automatically fill in all the
     * fields we received and navigate back here.
     */
    private fun registerMetadataSearchResult() {
        parentFragmentManager.setFragmentResultListener(
                MetadataSearch.REQUEST_KEY_METADATA_RESULT,
                viewLifecycleOwner
        ) { _, bundle ->
            val result = bundle.parcelable<MetadataSearchResult>(MetadataSearch.KEY_RESULT)
            if (result != null) {
                applySearchResult(result)
            }
        }
    }

    /**
     * Applies all available fields from [result] to the editor's input fields.
     * Only non-null values overwrite the current text — fields absent from the
     * result are left as they are so the user doesn't lose anything they typed.
     */
    private fun applySearchResult(result: MetadataSearchResult) {
        with(binding) {
            if (result.title.isNotBlank()) titleInput.setText(result.title)
            if (result.artist.isNotBlank()) artistInput.setText(result.artist)
            result.album?.takeIf { it.isNotBlank() }?.let { albumInput.setText(it) }
            result.year?.takeIf { it.isNotBlank() }?.let { yearInput.setText(it) }
            result.genre?.takeIf { it.isNotBlank() }?.let { genreInput.setText(it) }
        }
    }

    /**
     * Populates all input fields from the [audio] object passed via arguments.
     */
    private fun populateFields() {
        with(binding) {
            titleInput.setText(audio.title.orEmpty())
            artistInput.setText(audio.artist.orEmpty())
            albumInput.setText(audio.album.orEmpty())
            albumArtistInput.setText(audio.albumArtist.orEmpty())
            yearInput.setText(audio.year.orEmpty())
            genreInput.setText(audio.genre.orEmpty())
            trackNumberInput.setText(audio.trackNumber.orEmpty())
            numTracksInput.setText(audio.numTracks.orEmpty())
            composerInput.setText(audio.composer.orEmpty())
            writerInput.setText(audio.writer.orEmpty())
            compilationInput.setText(audio.compilation.orEmpty())

            // Disc number is stored as "X/Y" — we split it into the two separate fields.
            val discParts = audio.discNumber.orEmpty().split("/")
            discNumberInput.setText(discParts.getOrNull(0).orEmpty())
            discTotalInput.setText(discParts.getOrNull(1).orEmpty())

            rgTrackGainInput.setText(audio.replayGainTrackGain.orEmpty())
            rgTrackPeakInput.setText(audio.replayGainTrackPeak.orEmpty())
            rgAlbumGainInput.setText(audio.replayGainAlbumGain.orEmpty())
            rgAlbumPeakInput.setText(audio.replayGainAlbumPeak.orEmpty())
        }

        binding.albumArt.loadArtCoverWithPayload(audio)
    }

    /**
     * Wires the album art thumbnail and the label overlay to launch the
     * system image picker.
     */
    private fun setupAlbumArtPicker() {
        val launchPicker = View.OnClickListener {
            pickArtwork.launch(PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.albumArt.setOnClickListener(launchPicker)
    }

    /**
     * Copies the artwork selected by the user into a temporary file, decodes
     * it into a Bitmap for preview, and stores the file reference in
     * [pendingArtworkFile] for use at save time.
     *
     * @param uri The content URI returned by the image picker.
     */
    private fun handlePickedArtwork(uri: Uri) {
        runCatching {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: return

            val tempFile = File(requireContext().cacheDir, "pending_artwork_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()

            pendingArtworkFile = tempFile

            val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            binding.albumArt.setImageBitmap(bitmap)
        }.onFailure {
            Log.e(TAG, "Failed to load picked artwork: ${it.message}", it)
        }
    }

    /**
     * Observes [MetadataEditorViewModel.isSaving] and [MetadataEditorViewModel.saveResult]
     * to react to the outcome of save operations.
     */
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSaving.collect { saving ->
                binding.saveButton.isEnabled = !saving
                binding.saveButton.alpha = if (saving) 0.5f else 1.0f
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveResult.collect { result ->
                when (result) {
                    is MetadataEditorViewModel.SaveResult.Success -> {
                        showWarning(R.string.metadata_saved)
                        popBackStack()
                    }
                    is MetadataEditorViewModel.SaveResult.Error -> {
                        showWarning(result.message)
                    }
                }
            }
        }
    }

    /**
     * Reads all input fields, builds a [app.simple.felicity.repository.metadata.MetadataWriter.Fields] instance, applies
     * it to a copy of the original [Audio] to keep the Room row consistent, then
     * delegates to [MetadataEditorViewModel.saveMetadata].
     */
    private fun saveMetadata() {
        // Combine the two disc number inputs back into the "X/Y" format TagLib expects.
        val discNum = binding.discNumberInput.text?.toString()?.trim().orEmpty()
        val discTotal = binding.discTotalInput.text?.toString()?.trim().orEmpty()
        val discString = when {
            discNum.isNotEmpty() && discTotal.isNotEmpty() -> "$discNum/$discTotal"
            discNum.isNotEmpty() -> discNum
            else -> null
        }

        val fields = MetadataWriter.Fields(
                title = binding.titleInput.text?.toString()?.trim(),
                artist = binding.artistInput.text?.toString()?.trim(),
                album = binding.albumInput.text?.toString()?.trim(),
                albumArtist = binding.albumArtistInput.text?.toString()?.trim(),
                year = binding.yearInput.text?.toString()?.trim(),
                trackNumber = binding.trackNumberInput.text?.toString()?.trim(),
                numTracks = binding.numTracksInput.text?.toString()?.trim(),
                discNumber = discString,
                genre = binding.genreInput.text?.toString()?.trim(),
                composer = binding.composerInput.text?.toString()?.trim(),
                writer = binding.writerInput.text?.toString()?.trim(),
                compilation = binding.compilationInput.text?.toString()?.trim(),
                comment = binding.commentInput.text?.toString()?.trim(),
                lyrics = binding.lyricsInput.text?.toString()?.trim(),
                replayGainTrackGain = binding.rgTrackGainInput.text?.toString()?.trim(),
                replayGainTrackPeak = binding.rgTrackPeakInput.text?.toString()?.trim(),
                replayGainAlbumGain = binding.rgAlbumGainInput.text?.toString()?.trim(),
                replayGainAlbumPeak = binding.rgAlbumPeakInput.text?.toString()?.trim(),
        )

        val updatedAudio = audio.copy().apply {
            setTitle(fields.title)
            artist = fields.artist
            album = fields.album
            albumArtist = fields.albumArtist
            year = fields.year
            trackNumber = fields.trackNumber
            numTracks = fields.numTracks
            discNumber = discString
            genre = fields.genre
            composer = fields.composer
            writer = fields.writer
            compilation = fields.compilation
            replayGainTrackGain = fields.replayGainTrackGain
            replayGainTrackPeak = fields.replayGainTrackPeak
            replayGainAlbumGain = fields.replayGainAlbumGain
            replayGainAlbumPeak = fields.replayGainAlbumPeak
        }

        viewModel.saveMetadata(fields, updatedAudio, pendingArtworkFile)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingArtworkFile?.delete()
        pendingArtworkFile = null
    }

    companion object {
        const val TAG = "MetadataEditor"

        /**
         * Creates a new [MetadataEditor] instance with the given [audio] track.
         *
         * @param audio The [Audio] track whose metadata will be edited.
         * @return A configured [MetadataEditor] fragment.
         */
        fun newInstance(audio: Audio): MetadataEditor {
            return MetadataEditor().apply {
                arguments = Bundle().apply {
                    putParcelable(BundleConstants.AUDIO, audio)
                }
            }
        }
    }
}