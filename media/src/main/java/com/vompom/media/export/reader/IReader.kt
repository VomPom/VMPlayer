package com.vompom.media.export.reader

/**
 *
 * Created by @juliswang on 2025/11/05 21:38
 *
 * @Description
 */

interface IReader {
    fun listen(
        onBufferRead: (Long) -> Unit,
        onFinished: () -> Unit
    )
    fun start()
    fun stop()
    fun isRunning(): Boolean
}