package app.simple.felicity.ui.main.preferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterTypefaceItem
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.ScopedFragment

class TypeFaceSelection : ScopedFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = AdapterTypefaceItem()
    }

    companion object {
        fun newInstance(): TypeFaceSelection {
            val args = Bundle()
            val fragment = TypeFaceSelection()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "TypeFaceSelection"
    }
}