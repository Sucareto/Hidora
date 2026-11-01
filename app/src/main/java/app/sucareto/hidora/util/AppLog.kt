package app.sucareto.hidora.util

import android.util.Log
import app.sucareto.hidora.BuildConfig

object AppLog {
    inline fun i(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, msg())
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
    }
}
