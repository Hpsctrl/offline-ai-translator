package com.example.offlineaitranslator.engine

object AppLog {
    fun d(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun i(tag: String, msg: String) {
        try {
            android.util.Log.i(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] $msg")
        }
    }

    fun w(tag: String, msg: String) {
        try {
            android.util.Log.w(tag, msg)
        } catch (e: Throwable) {
            println("[$tag] WARN: $msg")
        }
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (e: Throwable) {
            println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
            tr?.printStackTrace()
        }
    }
}
