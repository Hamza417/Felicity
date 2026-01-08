package app.simple.felicity.dialogs.genres

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogGenreMenuBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.GenresPreferences

class DialogGenreMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogGenreMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogGenreMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.genreCover.isChecked = GenresPreferences.isGenreCoversEnabled()

        binding.genreCover.setOnCheckedChangeListener { switch, bool ->
            GenresPreferences.setGenreCoversEnabled(bool)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.SHOW_GENRE_COVERS -> {
                // Handle show genre covers change if needed
            }
        }
    }

    companion object {
        fun newInstance(): DialogGenreMenu {
            val args = Bundle()
            val fragment = DialogGenreMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showGenreMenu(): DialogGenreMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        private const val TAG = "DialogGenreMenu"
    }
}