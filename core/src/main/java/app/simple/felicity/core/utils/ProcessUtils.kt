package app.simple.felicity.core.utils

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessUtils {
    inline fun <T> ensureNotOnMainThread(block: () -> T): T {
        check(Thread.currentThread() != Looper.getMainLooper().thread) {
            "This function cannot be called on main thread"
        }

        return block()
    }

    inline fun <T> ensureOnMainThread(block: () -> T): T {
        check(Thread.currentThread() == Looper.getMainLooper().thread) {
            "This function should only be called on main thread"
        }

        return block()
    }

    inline fun <T> withDelay(delay: Long, crossinline block: () -> T) {
        ensureOnMainThread {
            Handler(Looper.getMainLooper()).postDelayed({
                                                                       block()
                                                                   }, delay)
        }
    }

    suspend inline fun <T> mainThread(crossinline block: () -> T) {
        withContext(Dispatchers.Main) {
            block()
        }
    }
}