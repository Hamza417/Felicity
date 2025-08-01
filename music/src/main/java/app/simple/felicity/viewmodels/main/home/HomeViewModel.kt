package app.simple.felicity.viewmodels.main.home

import android.app.Application
import app.simple.felicity.extensions.viewmodels.WrappedViewModel

class HomeViewModel(application: Application) : WrappedViewModel(application) {

    init {
        loadData()
    }

    private fun loadData() {

    }

    companion object {
        const val TAG = "HomeViewModel"
        private const val TAKE_COUNT = 18
    }
}
