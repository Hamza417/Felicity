package app.simple.felicity.dialogs.favorites

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment

/**
 * Bottom-sheet menu dialog for the Favorites panel.
 *
 * @author Hamza417
 */
class FavoritesMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
    }

    companion object {
        private const val TAG = "FavoritesMenu"

        fun newInstance(): FavoritesMenu {
            val args = Bundle()
            val fragment = FavoritesMenu()
            fragment.arguments = args
            return fragment
        }

        /**
         * Shows a [FavoritesMenu] bottom-sheet from the given [FragmentManager].
         */
        fun FragmentManager.showFavoritesMenu(): FavoritesMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

