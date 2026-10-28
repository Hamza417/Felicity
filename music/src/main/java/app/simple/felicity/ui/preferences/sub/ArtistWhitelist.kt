package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.ui.lists.AdapterArtistWhitelist
import app.simple.felicity.databinding.DialogAddWhitelistedArtistBinding
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.decorations.popups.SimpleDialog
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.LibraryPreferences

class ArtistWhitelist : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        requireHiddenMiniPlayer()

        val adapter = buildAdapter()
        binding.recyclerView.adapter = adapter
        view.startTransitionOnPreDraw()
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
