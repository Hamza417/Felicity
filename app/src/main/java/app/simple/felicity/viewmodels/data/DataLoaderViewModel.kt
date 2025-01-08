package app.simple.felicity.viewmodels.data

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.simple.felicity.extensions.viewmodels.WrappedViewModel
import app.simple.felicity.repository.workers.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DataLoaderViewModel(application: Application) : WrappedViewModel(application) {

    private val workManager = WorkManager.getInstance(application)

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> get() = _isCompleted

    fun startSyncWorker() {
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
        workManager.enqueueUniqueWork(SyncWorker.TAG, ExistingWorkPolicy.KEEP, syncWorkRequest)

        viewModelScope.launch {
            workManager.getWorkInfoByIdLiveData(syncWorkRequest.id).observeForever { workInfo ->
                if (workInfo != null && workInfo.state == WorkInfo.State.SUCCEEDED) {
                    _isCompleted.value = true
                }
            }
        }
    }
}
