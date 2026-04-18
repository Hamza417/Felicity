package app.simple.felicity.ui.preferences.sub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import app.simple.felicity.adapters.preference.AdapterTypeface
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.viewmodels.panels.TypeFacesViewModel

/**
 * The typeface picker panel. Shows every font family the app supports and lets
 * the user tap one to switch the whole app's font — pretty powerful for a
 * single screen, honestly.
 *
 * Fonts are heavy to load from assets, so we warm them all up in a background
 * thread via [TypeFacesViewModel] before the list is shown. When the user
 * leaves this panel, the ViewModel automatically clears those fonts from
 * memory so we're not wasting RAM on fonts no one is looking at.
 *
 * @author Hamza417
 */
class TypeFaces : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    /**
     * This ViewModel is responsible for loading all fonts in the background
     * and cleaning them up when this panel is destroyed.
     */
    private lateinit var typeFacesViewModel: TypeFacesViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        // Spin up the ViewModel — it immediately starts loading fonts in the background.
        typeFacesViewModel = ViewModelProvider(this)[TypeFacesViewModel::class.java]

        // Wait until the ViewModel signals that fonts are warm and ready, then
        // attach the adapter. This way the list renders without any stutter.
        typeFacesViewModel.typefaceCache.observe(viewLifecycleOwner) {
            binding.recyclerView.adapter = AdapterTypeface()
            binding.recyclerView.scheduleLayoutAnimation()
            view.startTransitionOnPreDraw()
        }

        requireHiddenMiniPlayer()
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.DRIFT
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): TypeFaces {
            val args = Bundle()
            val fragment = TypeFaces()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "TypeFaces"
    }
}