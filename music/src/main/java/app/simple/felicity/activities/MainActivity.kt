package app.simple.felicity.activities

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import app.simple.felicity.R
import app.simple.felicity.adapters.ui.miniplayer.AdapterMiniPlayerArtPager
import app.simple.felicity.callbacks.MiniPlayerCallbacks
import app.simple.felicity.core.utils.ViewUtils.gone
import app.simple.felicity.core.utils.ViewUtils.visible
import app.simple.felicity.databinding.ActivityMainBinding
import app.simple.felicity.dialogs.app.VolumeKnob.Companion.showVolumeKnob
import app.simple.felicity.extensions.activities.BaseActivity
import app.simple.felicity.preferences.HomePreferences
import app.simple.felicity.repository.managers.MediaManager
import app.simple.felicity.repository.models.Song
import app.simple.felicity.shared.utils.ConditionUtils.isNull
import app.simple.felicity.ui.main.home.ArtFlowHome
import app.simple.felicity.ui.main.home.CarouselHome
import app.simple.felicity.ui.main.home.SimpleHome
import app.simple.felicity.ui.main.home.SpannedHome
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity(), MiniPlayerCallbacks {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (savedInstanceState.isNull()) {
            setHomePanel()
        }
    }

    private fun setHomePanel() {
        when (HomePreferences.getHomeInterface()) {
            HomePreferences.HOME_INTERFACE_CAROUSEL -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, CarouselHome.newInstance(), CarouselHome.TAG)
                    .commit()
            }

            HomePreferences.HOME_INTERFACE_SPANNED -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SpannedHome.newInstance(), SpannedHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_ARTFLOW -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, ArtFlowHome.newInstance(), ArtFlowHome.TAG)
                    .commit()
            }
            HomePreferences.HOME_INTERFACE_SIMPLE -> {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.app_container, SimpleHome.newInstance(), SimpleHome.TAG)
                    .commit()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                showVolumeKnob()
                true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                showVolumeKnob()
                true
            }

            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onSongs(songs: List<Song>) {
        binding.miniAlbumArtPager.adapter = AdapterMiniPlayerArtPager(songs)

        binding.miniAlbumArtPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                MediaManager.setSongs(songs, position)
                binding.title.text = songs[position].title
                binding.artist.text = songs[position].artist
            }
        })

        binding.next.setOnClickListener {
            MediaManager.next()
        }

        binding.previous.setOnClickListener {
            MediaManager.previous()
        }

        binding.play.setOnClickListener {
            MediaManager.flipState()
        }
    }

    override fun onPositionChanged(position: Int) {
        binding.miniAlbumArtPager.setCurrentItem(position, true)
        binding.title.text = MediaManager.getSongAt(position)?.title ?: ""
        binding.artist.text = MediaManager.getSongAt(position)?.artist ?: ""
    }

    override fun onHideMiniPlayer() {
        binding.miniPlayerContainer.gone(true)
    }

    override fun onShowMiniPlayer() {
        binding.miniPlayerContainer.visible(true)
    }
}
