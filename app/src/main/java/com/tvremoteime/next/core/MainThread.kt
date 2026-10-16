package com.tvremoteime.next.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

object MainThread {
    private val handler = Handler(Looper.getMainLooper())

    fun call(action: () -> Boolean): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(action)
        handler.post(task)
        return try {
            task.get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
            task.cancel(false)
            handler.removeCallbacks(task)
            false
        }
    }
}
