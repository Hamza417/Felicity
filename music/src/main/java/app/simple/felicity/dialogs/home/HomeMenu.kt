package app.simple.felicity.dialogs.home

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.constants.CommonPreferencesConstants
import app.simple.felicity.databinding.DialogHomeMenuBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.HomePreferences
import com.google.android.material.button.MaterialButtonToggleGroup

class HomeMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogHomeMenuBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogHomeMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set initial toggle state
        val currentType = HomePreferences.getHomeLayoutType()
        binding.gridType.check(
                if (currentType == CommonPreferencesConstants.GRID_TYPE_GRID) {
                    binding.grid.id
                } else {
                    binding.list.id
                }
        )

        binding.gridType.addOnButtonCheckedListener(object : MaterialButtonToggleGroup.OnButtonCheckedListener {
            override fun onButtonChecked(group: MaterialButtonToggleGroup?, checkedId: Int, isChecked: Boolean) {
                if (!isChecked) return
                when (checkedId) {
                    binding.list.id -> {
                        HomePreferences.setHomeLayoutType(CommonPreferencesConstants.GRID_TYPE_LIST)
                    }
                    binding.grid.id -> {
                        HomePreferences.setHomeLayoutType(CommonPreferencesConstants.GRID_TYPE_GRID)
                    }
                }
            }
        })

        binding.reset.setOnClickListener {
            HomePreferences.resetHomeOrder()
        }

        binding.openAppSettings.setOnClickListener {
            openAppSettings()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            HomePreferences.HOME_INTERFACE -> {
                // handled elsewhere
            }
        }
    }

    companion object {
        fun newInstance(): HomeMenu {
            val args = Bundle()
            val fragment = HomeMenu()
            fragment.arguments = args
            return fragment
        }

        private const val TAG = "HomeMenu"

        fun FragmentManager.showHomeMenu(): HomeMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

