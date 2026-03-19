package app.simple.felicity.dialogs.songs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.extensions.fragments.ScopedFragment
import app.simple.felicity.ui.panels.ArtFlow

class SongsMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.artflow.setOnClickListener {
            (parentFragment as? ScopedFragment)?.openFragment(ArtFlow.newInstance(), ArtFlow.TAG).also {
                dismiss()
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {

        }
    }

    companion object {
        fun newInstance(): SongsMenu {
            val args = Bundle()
            val fragment = SongsMenu()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "SongsMenu"

        fun FragmentManager.showSongsMenu(): SongsMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}