package com.vompom.media.export

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/06 10:42
 *
 * @Description
 */

interface IMediaMuxer {
    fun start()
    fun stop()
    fun addTrack(format: MediaFormat): Int
    fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun release()
}