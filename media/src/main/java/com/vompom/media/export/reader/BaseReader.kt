package com.vompom.media.export.reader

import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/11/05 21:36
 *
 * @Description
 */

abstract class BaseReader(val segments: List<TrackSegment>) : IReader {
    private var totalDurationUs = -1L
    private var isStop = false

    protected var onBufferRead: ((Long) -> Unit)? = null
    protected var onFinished: (() -> Unit)? = null

    protected var readTimeUs = 0L

    override fun listen(onBufferRead: (Long) -> Unit, onFinished: () -> Unit) {
        this.onBufferRead = onBufferRead
        this.onFinished = onFinished
    }

    fun durationUs(): Long {
        if (totalDurationUs < 0) {
            totalDurationUs = segments.sumOf { it.timelineRange.durationUs }
        }
        return totalDurationUs
    }

    override fun stop() {
        isStop = true
    }

    override fun start() {
        Thread {
            prepare()
            onStart()
        }.start()
    }

    override fun isRunning(): Boolean = !isStop

    abstract fun prepare()

    abstract fun onStart()
}