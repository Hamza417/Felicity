package app.simple.felicity.dialogs.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogArtistSeparatorsBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.LibraryPreferences

class ArtistSeparators : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogArtistSeparatorsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogArtistSeparatorsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.editText.setText(LibraryPreferences.getArtistSeparators())
        binding.editText.setSelection(binding.editText.text?.length ?: 0)

        binding.cancel.setOnClickListener {
            dismiss()
        }

        binding.save.setOnClickListener {
            LibraryPreferences.setArtistSeparators(binding.editText.text.toString().trim())
            dismiss()
        }
    }

    companion object {
        fun newInstance(): ArtistSeparators {
            val args = Bundle()
            val fragment = ArtistSeparators()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showArtistSeparators(): ArtistSeparators {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        const val TAG = "ArtistSeparators"
    }
}
