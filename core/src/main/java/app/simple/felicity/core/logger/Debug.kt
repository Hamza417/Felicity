package app.simple.felicity.core.logger

import android.util.Log

object Debug {
    private const val TAG = "Debug"

    fun printLog(message: String) {
        println("$TAG: $message")
    }

    fun Any.logDebug(message: String) {
        try {
            val tag = this::class.java.getDeclaredField("TAG").get(null) as? String
                ?: this::class.java.simpleName
            Log.d(tag, message)
        } catch (e: NoSuchFieldException) {
            Log.d(this::class.java.simpleName, message)
        } catch (e: IllegalAccessException) {
            Log.d(this::class.java.simpleName, message)
        }
    }

    fun Any.logInfo(message: String) {
        try {
            val tag = this::class.java.getDeclaredField("TAG").get(null) as? String
                ?: this::class.java.simpleName
            Log.i(tag, message)
        } catch (e: NoSuchFieldException) {
            Log.i(this::class.java.simpleName, message)
        } catch (e: IllegalAccessException) {
            Log.i(this::class.java.simpleName, message)
        }
    }
}