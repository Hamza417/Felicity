package app.simple.felicity.utils

import android.os.Looper

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
            android.os.Handler(Looper.getMainLooper()).postDelayed({
                                                                       block()
                                                                   }, delay)
        }
    }
}