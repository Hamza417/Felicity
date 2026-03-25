package app.simple.felicity.dialogs.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.simple.felicity.adapters.ui.lists.AdapterMilkdropPresets
import app.simple.felicity.databinding.DialogMilkdropPresetsBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.milkdrop.models.MilkdropPreset
import app.simple.felicity.preferences.MilkdropPreferences
import app.simple.felicity.viewmodels.dialogs.MilkdropPresetsViewModel
import kotlinx.coroutines.launch

/**
 * Bottom-sheet dialog that lists all bundled Milkdrop presets and lets the user
 * pick one.  The selected preset path is persisted via [MilkdropPreferences] so
 * the parent [app.simple.felicity.ui.panels.Milkdrop] fragment can observe the
 * [SharedPreferences][android.content.SharedPreferences] change and load the new
 * preset into projectM.
 *
 * @author Hamza417
 */
class MilkdropPresets : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogMilkdropPresetsBinding
    private var adapter: AdapterMilkdropPresets? = null

    private val viewModel: MilkdropPresetsViewModel by viewModels()

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = DialogMilkdropPresetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentPath = MilkdropPreferences.getLastPreset()

        adapter = AdapterMilkdropPresets { preset ->
            onPresetSelected(preset)
        }.also { a ->
            a.setSelectedPath(currentPath)
            binding.presetsRecyclerView.adapter = a
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { list ->
                    adapter?.submitList(list)
                    // Scroll to the selected preset so it is immediately visible.
                    val selectedIndex = list.indexOfFirst { it.path == currentPath }
                    if (selectedIndex >= 0) {
                        binding.presetsRecyclerView.scrollToPosition(selectedIndex)
                    }
                }
            }
        }
    }

    private fun onPresetSelected(preset: MilkdropPreset) {
        MilkdropPreferences.setLastPreset(preset.path)
        dismissAllowingStateLoss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
    }

    companion object {
        fun newInstance(): MilkdropPresets {
            return MilkdropPresets().apply {
                arguments = Bundle()
            }
        }

        fun FragmentManager.showMilkdropPresets() {
            newInstance().show(this, TAG)
        }

        const val TAG = "MilkdropPresets"
    }
}