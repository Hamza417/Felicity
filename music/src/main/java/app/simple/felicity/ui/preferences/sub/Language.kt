package app.simple.felicity.ui.preferences.sub

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.simple.felicity.adapters.preference.AdapterLanguage
import app.simple.felicity.databinding.FragmentGenericRecyclerViewBinding
import app.simple.felicity.extensions.fragments.MediaFragment
import app.simple.felicity.preferences.ConfigurationPreferences

class Language : MediaFragment() {

    private lateinit var binding: FragmentGenericRecyclerViewBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentGenericRecyclerViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.recyclerView.adapter = AdapterLanguage()
        binding.recyclerView.scheduleLayoutAnimation()

        view.startTransitionOnPreDraw()
        requireHiddenMiniPlayer()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        when (key) {
            ConfigurationPreferences.LANGUAGE -> {
                (binding.recyclerView.adapter as? AdapterLanguage)?.notifyDataSetChanged()
            }
        }
    }

    override fun getTransitionType(): TransitionType {
        return TransitionType.SLIDE
    }

    override val wantsMiniPlayerVisible: Boolean
        get() = false

    companion object {
        fun newInstance(): Language {
            val args = Bundle()
            val fragment = Language()
            fragment.arguments = args
            return fragment
        }

        const val TAG = "TypeFaces"
    }
}