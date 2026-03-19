package app.simple.felicity.dialogs.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import app.simple.felicity.adapters.home.main.AdapterHomeOrganize
import app.simple.felicity.databinding.DialogHomeOrganizeBinding
import app.simple.felicity.extensions.dialogs.ScopedBottomSheetFragment
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.viewmodels.panels.SimpleHomeViewModel

/**
 * Bottom sheet dialog that lets the user passively organize the Simple Home
 * grid/list order using explicit drag handles.
 *
 * Drag-to-organize is intentionally kept inside this dialog so that the
 * main home list remains a flat, scroll-only surface — avoiding conflicts
 * between scroll gestures and drag gestures.
 *
 * @author Hamza417
 */
class HomeOrganize : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogHomeOrganizeBinding
    private var homeViewModel: SimpleHomeViewModel? = null
    private var adapterHomeOrganize: AdapterHomeOrganize? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = DialogHomeOrganizeBinding.inflate(inflater, container, false)
        homeViewModel = ViewModelProvider(requireActivity())[SimpleHomeViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        homeViewModel!!.getHomeData().observe(viewLifecycleOwner) { elements ->
            if (adapterHomeOrganize == null) {
                adapterHomeOrganize = AdapterHomeOrganize(elements.toMutableList())
                binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
                binding.recyclerView.adapter = adapterHomeOrganize
                adapterHomeOrganize!!.attachItemTouchHelper(binding.recyclerView)
            }
        }

        binding.reset.setOnClickListener {
            HomePreferences.resetHomeOrder()
            // Reload fresh data after reset and rebuild the adapter
            adapterHomeOrganize = null
            homeViewModel?.reloadHomeData()
        }

        binding.done.setOnClickListener {
            adapterHomeOrganize?.getCurrentOrder()?.let { ordered ->
                homeViewModel?.updateOrder(ordered)
            }
            dismiss()
        }
    }

    companion object {
        private const val TAG = "HomeOrganize"

        fun newInstance(): HomeOrganize {
            val args = Bundle()
            val fragment = HomeOrganize()
            fragment.arguments = args
            return fragment
        }

        /**
         * Convenience extension to show this dialog from a [FragmentManager].
         */
        fun FragmentManager.showHomeOrganize(): HomeOrganize {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }
    }
}

