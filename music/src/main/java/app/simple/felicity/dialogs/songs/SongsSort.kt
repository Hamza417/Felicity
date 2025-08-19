package app.simple.felicity.dialogs.songs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSortSongsBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment

class SongsSort : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSortSongsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSortSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        fun newInstance(): SongsSort {
            val args = Bundle()
            val fragment = SongsSort()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showSongsSort(): SongsSort {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "SongsSort"
    }
}