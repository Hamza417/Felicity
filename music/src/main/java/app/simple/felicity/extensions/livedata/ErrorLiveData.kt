package app.simple.felicity.extensions.livedata

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import app.simple.felicity.core.utils.StackTraceUtils
import app.simple.felicity.repository.database.instances.StackTraceDatabase
import app.simple.felicity.repository.models.normal.StackTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

open class ErrorLiveData : MutableLiveData<Throwable>() {

    override fun postValue(value: Throwable?) {
        super.postValue(value)
    }

    fun postError(value: Throwable, application: Application) {
        postValue(value)
        saveTraceToDatabase(value, application.applicationContext)
    }

    private fun saveTraceToDatabase(throwable: Throwable, applicationContext: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val stacktrace: Writer = StringWriter()
            val printWriter = PrintWriter(stacktrace)
            throwable.printStackTrace(printWriter)
            printWriter.close()

            throwable.printStackTrace()
            StackTraceDatabase.getInstance(applicationContext)
                ?.stackTraceDao()?.insertTrace(
                    StackTrace(
                        stacktrace.toString(),
                        throwable.localizedMessage ?: throwable.toString(),
                        StackTraceUtils.getCause(throwable).toString(),
                        System.currentTimeMillis()
                    )
                )
        }
    }
}
