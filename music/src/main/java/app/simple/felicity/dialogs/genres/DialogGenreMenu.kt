package app.simple.felicity.dialogs.genres

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogGenreMenuBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.popups.genres.PopupGenreGridSizeMenu
import app.simple.felicity.preferences.GenresPreferences

class DialogGenreMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogGenreMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogGenreMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setGridSizeValue()

        binding.gridSize.setOnClickListener { it ->
            PopupGenreGridSizeMenu(
                    container = binding.container,
                    anchorView = it,
                    menuItems = listOf(R.string.one,
                                       R.string.two,
                                       R.string.three,
                                       R.string.four,
                                       R.string.five,
                                       R.string.six),
                    menuIcons = listOf(R.drawable.ic_one_16,
                                       R.drawable.ic_two_16dp,
                                       R.drawable.ic_three_16dp,
                                       R.drawable.ic_four_16dp,
                                       R.drawable.ic_five_16dp,
                                       R.drawable.ic_six_16dp),
                    onMenuItemClick = {
                        when (it) {
                            R.string.one -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_ONE)
                            R.string.two -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_TWO)
                            R.string.three -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_THREE)
                            R.string.four -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_FOUR)
                            R.string.five -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_FIVE)
                            R.string.six -> GenresPreferences.setGridSize(GenresPreferences.GRID_SIZE_SIX)
                        }

                        setGridSizeValue()
                    },
                    onDismiss = {

                    }
            ).show()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            GenresPreferences.GRID_SIZE -> {
                setGridSizeValue()
            }
            GenresPreferences.GRID_SPACING -> {
                // Handle grid spacing change if needed
            }
            GenresPreferences.SHOW_GENRE_COVERS -> {
                // Handle show genre covers change if needed
            }
        }
    }

    private fun setGridSizeValue() {
        // Set the grid size value based on user preferences
        val gridSize = GenresPreferences.getGridSize()
        when (gridSize) {
            GenresPreferences.GRID_SIZE_ONE -> binding.gridSize.text = getString(R.string.one)
            GenresPreferences.GRID_SIZE_TWO -> binding.gridSize.text = getString(R.string.two)
            GenresPreferences.GRID_SIZE_THREE -> binding.gridSize.text = getString(R.string.three)
            GenresPreferences.GRID_SIZE_FOUR -> binding.gridSize.text = getString(R.string.four)
            GenresPreferences.GRID_SIZE_FIVE -> binding.gridSize.text = getString(R.string.five)
            GenresPreferences.GRID_SIZE_SIX -> binding.gridSize.text = getString(R.string.six)
            else -> binding.gridSize.text = getString(R.string.two) // Default to two columns
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