package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import app.simple.felicity.adapters.ui.lists.AdapterArtistWhitelist
import app.simple.felicity.databinding.DialogAddWhitelistedArtistBinding
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LibraryPreferences
import app.simple.felicity.repository.repositories.ArtistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArtistWhitelist : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding
    private val allArtists = mutableListOf<String>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireHiddenMiniPlayer()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val artists = ArtistRepository(requireContext())
                .fetchArtists()
                .mapNotNull {
                    it.name
                }.distinct()

            allArtists.clear()
            allArtists.addAll(artists)
        }

        val adapter = buildAdapter()
        binding.recyclerView.adapter = adapter
    }

    private fun buildAdapter(): AdapterArtistWhitelist {
        val adapter = AdapterArtistWhitelist(
                onAdd = {
                    showAddDialog()
                },
                onRemove = { artist ->
                    LibraryPreferences.removeWhitelistedArtist(artist)
                    refreshList()
                }
        )

        adapter.submitList(LibraryPreferences.getArtistWhitelist().toList().sorted())
        return adapter
    }

    private fun showAddDialog() {
        SimpleDialog.Builder(
                container = requireContainerView(),
                inflateBinding = DialogAddWhitelistedArtistBinding::inflate)
            .onViewCreated { _ ->
                /* no-op */
            }.onDialogInflated { dialogBinding, dismiss, _ ->
                dialogBinding.editText.showInput()

                dialogBinding.editText.doAfterTextChanged { s ->
                    val input = s?.toString() ?: ""

                    if (input.isBlank()) {
                        dialogBinding.editText.clearHint()
                        return@doAfterTextChanged
                    }

                    val match = allArtists.firstOrNull {
                        it.startsWith(input, ignoreCase = true)
                    }

                    if (match != null) {
                        val remaining = match.substring(input.length)
                        dialogBinding.editText.finalVerificationHint = match
                        dialogBinding.editText.drawHint(input, remaining)
                    } else {
                        dialogBinding.editText.clearHint()
                    }
                }

                dialogBinding.save.setOnClickListener {
                    val label = dialogBinding.editText.text.toString().trim()

                    if (label.isNotEmpty()) {
                        LibraryPreferences.addWhitelistedArtist(label)
                        refreshList()
                        dismiss()
                    }
                }

                dialogBinding.cancel.setOnClickListener {
                    dismiss()
                }
            }.onDismiss {
                /* no-op */
            }
            .build()
            .show()
    }

    private fun refreshList() {
        (binding.recyclerView.adapter as? AdapterArtistWhitelist)?.submitList(
                LibraryPreferences.getArtistWhitelist().toList().sorted()
        )
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.DRIFT
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): ArtistWhitelist {
            return ArtistWhitelist()
        }

        const val TAG = "ArtistWhitelist"
    }
}
