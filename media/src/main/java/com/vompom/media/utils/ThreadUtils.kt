package com.vompom.media.utils

import android.os.Handler
import android.os.Looper

/**
 *
 * Created by @juliswang on 2025/11/10 21:09
 *
 * @Description
 */

object ThreadUtils {
    private val sMainThread = Looper.getMainLooper().thread


    private val mainHandler = Handler(Looper.getMainLooper())


    fun post(r: Runnable) {
        mainHandler.post(r)
    }

    fun postDelayed(r: Runnable, delayMillis: Long) {
        mainHandler.postDelayed(r, delayMillis)
    }

    /**
     *
     * Removes the specified Runnable from the **MAIN** message queue.
     *
     * @param r The Runnable to remove from the message handling queue
     * @see .post
     *
     * @see .postDelayed
     */
    fun removeCallbacks(r: Runnable) {
        mainHandler.removeCallbacks(r)
    }

    fun runInMainThread(runnable: Runnable) {
        if (isMainThread) {
            runnable.run()
        } else {
            post(runnable)
        }
    }

    val isMainThread: Boolean
        get() = sMainThread === Thread.currentThread()

}