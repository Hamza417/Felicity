package app.simple.felicity.dialogs.carousel

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import app.simple.felicity.databinding.DialogCarouselSettingsBinding
import app.simple.felicity.decorations.seekbars.FelicitySeekbar
import app.simple.felicity.extensions.fragments.ScopedBottomSheetFragment
import app.simple.felicity.preferences.CarouselPreferences

class CarouselMenu : ScopedBottomSheetFragment() {

    private lateinit var binding: DialogCarouselSettingsBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DialogCarouselSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cameraYSeekbar.setDefaultProgress(CarouselPreferences.CAMERA_EYE_Y_DEFAULT)
        binding.cameraYSeekbar.setProgress(CarouselPreferences.getEyeY())
        binding.zSpreadSeekbar.setDefaultProgress(CarouselPreferences.Z_SPREAD_DEFAULT)
        binding.zSpreadSeekbar.setProgress(CarouselPreferences.getZSpread())
        binding.reflectionGapSeekbar.setDefaultProgress(CarouselPreferences.REFLECTION_GAP_DEFAULT)
        binding.reflectionGapSeekbar.setProgress(CarouselPreferences.getReflectionGap())
        binding.scaleSeekbar.setDefaultProgress(CarouselPreferences.SCALE_DEFAULT)
        binding.scaleSeekbar.setProgress(CarouselPreferences.getScale())

        binding.cameraYSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    CarouselPreferences.setEyeY(progress)
                }
            }
        })

        binding.zSpreadSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    CarouselPreferences.setZSpread(progress)
                }
            }
        })

        binding.reflectionGapSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    CarouselPreferences.setReflectionGap(progress)
                }
            }
        })

        binding.scaleSeekbar.setOnSeekChangeListener(object : FelicitySeekbar.OnSeekChangeListener {
            override fun onProgressChanged(seekbar: FelicitySeekbar, progress: Float, fromUser: Boolean) {
                if (fromUser) {
                    CarouselPreferences.setScale(progress)
                }
            }
        })
    }

    companion object {
        fun newInstance(): CarouselMenu {
            val args = Bundle()
            val fragment = CarouselMenu()
            fragment.arguments = args
            return fragment
        }

        fun FragmentManager.showCarouselMenu(): CarouselMenu {
            val dialog = newInstance()
            dialog.show(this, TAG)
            return dialog
        }

        const val TAG = "DialogCarouselSettings"
    }
}