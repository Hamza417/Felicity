package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.GenericPreferencesAdapter
import app.simple.felicity.databinding.FragmentPreferenceAppearanceBinding
import app.simple.felicity.decorations.itemdecorations.SpacingItemDecoration
import app.simple.felicity.extensions.fragments.PreferenceFragment

class UserInterface : PreferenceFragment() {

    private lateinit var binding: FragmentPreferenceAppearanceBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentPreferenceAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.setHasFixedSize(false)
        binding.recyclerView.addItemDecoration(SpacingItemDecoration(24, true))
        binding.recyclerView.adapter = GenericPreferencesAdapter(createUserInterfacePanel())
    }

    companion object {
        fun newInstance(): UserInterface {
            val args = Bundle()
            val fragment = UserInterface()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "UserInterface"
    }
}