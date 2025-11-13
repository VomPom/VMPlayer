package com.vompom.media.export.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/05 21:59
 *
 * @Description
 */

abstract class BaseEncoder : IEncoder {
    companion object {
        const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
        const val TIMEOUT_US = 10000L
        private const val TAG = "BaseEncoder"
    }

    protected lateinit var encoder: MediaCodec
    protected var onBufferEncodeCallback: OnBufferEncode? = null
    private var trackIndex = -1
    private var isEOSReceived = false
    protected var encodePTS = 0L

    override fun prepare() {
        encoder = MediaCodec.createEncoderByType(encodeType()).apply {
            configure(encodeFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        onConfigure(encoder)
        encoder.start()
    }

    override fun listen(onFormatChange: (MediaFormat) -> Int, onBufferEncode: OnBufferEncode) {
        drainEncoder(
            onFormatChange = onFormatChange,
            onBufferEncode = onBufferEncode,
            waitForEOS = false
        )
    }

    /**
     * 持续drain编码器直到收到EOS
     * 用于finishEncoding时确保所有数据都被处理
     */
    protected fun drainEncoderUntilEOS(onBufferEncode: OnBufferEncode) {
        drainEncoder(
            onFormatChange = null,
            onBufferEncode = onBufferEncode,
            waitForEOS = true
        )
    }

    /**
     * 核心drain逻辑，统一处理编码器输出
     *
     * @param onFormatChange 格式变化回调，EOS阶段可以为null
     * @param onBufferEncode 缓冲区编码回调
     * @param waitForEOS 是否等待EOS，true表示持续drain直到EOS，false表示处理当前可用的数据后返回
     */
    private fun drainEncoder(
        onFormatChange: ((MediaFormat) -> Int)? = null,
        onBufferEncode: OnBufferEncode,
        waitForEOS: Boolean
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var eosReceived = false

        while (!eosReceived) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (waitForEOS) {
                        // EOS模式：继续等待
                        continue
                    } else {
                        // 普通模式：没有更多数据时退出
                        return
                    }
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (onFormatChange != null && trackIndex == -1) {
                        trackIndex = onFormatChange(encoder.outputFormat)
                        VLog.d(TAG, "Output format changed, track index: $trackIndex")
                    } else if (waitForEOS) {
                        VLog.d(TAG, "Output format changed during EOS drain")
                    }
                    continue // 继续处理下一个buffer
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let { buffer ->
                        if (bufferInfo.size > 0 && trackIndex != -1) {
                            try {
                                bufferInfo.presentationTimeUs = encodePTS
                                encodePTS += when (encodeType()) {
                                    VIDEO_MIME_TYPE -> 33_000
                                    AUDIO_MIME_TYPE -> 23_220
                                    else -> 0
                                }

                                onBufferEncode(trackIndex, buffer, bufferInfo)
                            } catch (e: Exception) {
                                VLog.e(TAG, "Error encoding buffer", e)
                                if (waitForEOS) {
                                    eosReceived = true // EOS模式下出错时停止drain
                                } else {
                                    return // 普通模式下出错时退出
                                }
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    // 检查EOS标志
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReceived = true
                        isEOSReceived = true
                        VLog.d(TAG, "EOS received for ${encodeType()}")
                    } else if (!waitForEOS) {
                        // 普通模式下处理完一个buffer就可以退出（可以继续循环处理更多buffer）
                        // 这里选择继续循环，直到没有更多可用的buffer
                    }
                }

                else -> {
                    VLog.w(TAG, "Unexpected output buffer index: $outputBufferIndex")
                    if (waitForEOS) {
                        continue // EOS模式继续尝试
                    } else {
                        return // 普通模式退出
                    }
                }
            }
        }
    }

    /**
     * 设置buffer编码回调，用于在EOS处理时写入数据到muxer
     */
    fun setBufferEncodeCallback(callback: OnBufferEncode) {
        this.onBufferEncodeCallback = callback
    }

    override fun release() {
        try {
            if (::encoder.isInitialized) {
                encoder.stop()
                encoder.release()
                VLog.d(TAG, "${encodeType()} encoder released")
            }
        } catch (e: Exception) {
            VLog.e(TAG, "Error releasing ${encodeType()} encoder", e)
        }
    }

    abstract fun encodeFormat(): MediaFormat

    abstract fun onConfigure(encoder: MediaCodec)

}
typealias OnBufferEncode = (Int, ByteBuffer, MediaCodec.BufferInfo) -> Unit