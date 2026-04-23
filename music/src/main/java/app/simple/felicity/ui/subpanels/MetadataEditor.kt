package app.simple.felicity.ui.subpanels

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * On save, [app.simple.felicity.viewmodels.panels.MetadataEditorViewModel] writes the changes to disk via
 * JAudioTagger, updates the Room database, and triggers a MediaStore rescan
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
            ActivityResultContracts.GetContent()
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

        binding.saveButton.setOnClickListener {
            saveMetadata()
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
            discNumberInput.setText(audio.discNumber.orEmpty())
            compilationInput.setText(audio.compilation.orEmpty())
            composerInput.setText(audio.composer.orEmpty())
            writerInput.setText(audio.writer.orEmpty())
        }

        binding.albumArt.loadArtCoverWithPayload(audio)
    }

    /**
     * Wires the album art thumbnail and the label overlay to launch the
     * system image picker.
     */
    private fun setupAlbumArtPicker() {
        val launchPicker = View.OnClickListener {
            pickArtwork.launch("image/*")
        }
        binding.albumArt.setOnClickListener(launchPicker)
        binding.pickArtLabel.setOnClickListener(launchPicker)
        binding.albumArtContainer.setOnClickListener(launchPicker)
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
        val fields = MetadataWriter.Fields(
                title = binding.titleInput.text?.toString()?.trim(),
                artist = binding.artistInput.text?.toString()?.trim(),
                album = binding.albumInput.text?.toString()?.trim(),
                albumArtist = binding.albumArtistInput.text?.toString()?.trim(),
                year = binding.yearInput.text?.toString()?.trim(),
                trackNumber = binding.trackNumberInput.text?.toString()?.trim(),
                numTracks = binding.numTracksInput.text?.toString()?.trim(),
                discNumber = binding.discNumberInput.text?.toString()?.trim(),
                genre = binding.genreInput.text?.toString()?.trim(),
                composer = binding.composerInput.text?.toString()?.trim(),
                writer = binding.writerInput.text?.toString()?.trim(),
                compilation = binding.compilationInput.text?.toString()?.trim(),
                comment = binding.commentInput.text?.toString()?.trim(),
                lyrics = binding.lyricsInput.text?.toString()?.trim(),
        )

        val updatedAudio = audio.copy().apply {
            setTitle(fields.title)
            setArtist(fields.artist)
            setAlbum(fields.album)
            setAlbumArtist(fields.albumArtist)
            setYear(fields.year)
            setTrackNumber(fields.trackNumber)
            setNumTracks(fields.numTracks)
            setDiscNumber(fields.discNumber)
            setGenre(fields.genre)
            setComposer(fields.composer)
            setWriter(fields.writer)
            setCompilation(fields.compilation)
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