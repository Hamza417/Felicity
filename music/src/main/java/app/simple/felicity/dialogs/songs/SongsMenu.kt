package app.simple.felicity.dialogs.songs

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.R
import app.simple.felicity.databinding.DialogSongsMenuBinding
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.popups.songs.PopupSongsInterfaceMenu
import app.simple.felicity.preferences.SongsPreferences

class SongsMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogSongsMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogSongsMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setSongsInterfaceType()

        binding.changeSongsInterface.setOnClickListener { it ->
            PopupSongsInterfaceMenu(
                    container = binding.container,
                    anchorView = it,
                    menuItems = listOf(R.string.app_name,
                                       R.string.artflow
                    ),
                    menuIcons = listOf(
                            R.drawable.ic_list_16dp,
                            R.drawable.ic_flow_16dp,
                    ),
                    onMenuItemClick = {
                        when (it) {
                            R.string.app_name -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FELICITY)
                            }
                            R.string.artflow -> {
                                SongsPreferences.setSongsInterface(SongsPreferences.SONG_INTERFACE_FLOW)
                            }
                        }
                    },
                    onDismiss = {

                    }
            ).show()
        }
    }

    private fun setSongsInterfaceType() {
        when (SongsPreferences.getSongsInterface()) {
            SongsPreferences.SONG_INTERFACE_FELICITY -> {
                binding.changeSongsInterface.text = getString(R.string.app_name)
            }
            SongsPreferences.SONG_INTERFACE_FLOW -> {
                binding.changeSongsInterface.text = getString(R.string.artflow)
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            SongsPreferences.SONGS_INTERFACE -> {
                setSongsInterfaceType()
            }
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