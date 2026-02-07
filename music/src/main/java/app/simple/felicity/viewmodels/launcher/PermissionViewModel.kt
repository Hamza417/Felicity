package app.simple.felicity.viewmodels.launcher

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PermissionViewModel : ViewModel() {
    private val manageFilesPermission = MutableLiveData<Boolean>()

    fun getManageFilesPermissionState(): LiveData<Boolean> {
        return manageFilesPermission
    }

    fun setManageFilesPermissionState(granted: Boolean) {
        manageFilesPermission.value = granted
    }
}