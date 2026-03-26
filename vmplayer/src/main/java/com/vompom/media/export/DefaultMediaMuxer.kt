package com.vompom.media.export

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/06 10:42
 *
 * @Description
 */

class DefaultMediaMuxer : IMediaMuxer {
    companion object {
        private const val TAG = "DefaultMediaMuxer"
    }

    private var muxer: MediaMuxer
    private var isStarted: Boolean = false
    private var isStopped: Boolean = false

    constructor(path: String) {
        muxer = MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun start() {
        if (isStarted || isStopped) {
            VLog.w(TAG, "Muxer already started or stopped, cannot start again")
            return
        }
        isStarted = true
        muxer.start()
        VLog.d(TAG, "MediaMuxer started")
    }

    override fun stop() {
        if (!isStarted || isStopped) {
            VLog.w(TAG, "Muxer not started or already stopped")
            return
        }
        try {
            muxer.stop()
            isStopped = true
            VLog.d(TAG, "MediaMuxer stopped")
        } catch (e: Exception) {
            VLog.e(TAG, "Error stopping MediaMuxer", e)
            isStopped = true // 标记为已停止，避免重复尝试
        }
    }

    override fun addTrack(format: MediaFormat): Int {
        if (isStarted) {
            VLog.w(TAG, "Cannot add track after muxer started")
            return -1
        }
        val trackIndex = muxer.addTrack(format)
        VLog.d(TAG, "Track added with index: $trackIndex")
        return trackIndex
    }

    override fun writeSampleData(
        trackIndex: Int,
        byteBuf: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        if (!isStarted) {
            VLog.w(TAG, "Muxer not started, cannot write sample data")
            return
        }

        if (isStopped) {
            VLog.w(TAG, "Muxer already stopped, cannot write sample data")
            return
        }
        if (bufferInfo.size <= 0) {
            VLog.w(TAG, "Invalid buffer size: ${bufferInfo.size}")
            return
        }

        try {
            muxer.writeSampleData(trackIndex, byteBuf, bufferInfo)
        } catch (e: IllegalStateException) {
            VLog.w(TAG, "Failed to write sample data - muxer may have stopped: ${e.message}")
            isStopped = true
        } catch (e: Exception) {
            VLog.e(TAG, "Error writing sample data", e)
        }
    }

    override fun release() {
        try {
            if (isStarted && !isStopped) {
                stop()
            }
            muxer.release()
            VLog.d(TAG, "MediaMuxer released")
        } catch (e: Exception) {
            VLog.e(TAG, "Error releasing MediaMuxer", e)
        }
    }
}