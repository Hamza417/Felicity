package app.simple.felicity.extensions.livedata

import androidx.lifecycle.MutableLiveData

open class ErrorLiveData : MutableLiveData<Throwable>() {

    override fun postValue(value: Throwable?) {
        super.postValue(value)
    }

    fun postError(value: Throwable) {
        postValue(value)
    }
}
