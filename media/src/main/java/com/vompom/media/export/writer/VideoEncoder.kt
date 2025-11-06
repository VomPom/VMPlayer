package com.vompom.media.export.writer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.vompom.media.export.EncodeManager.ExportConfig
import com.vompom.media.utils.VLog

/**
 *
 * Created by @juliswang on 2025/11/05 16:59
 *
 * @Description
 */

class VideoEncoder(val config: ExportConfig) : BaseEncoder() {
    private var surface: Surface? = null

    companion object {
        private const val TAG = "VideoEncoder"
    }

    override fun encodeType(): String {
        return VIDEO_MIME_TYPE
    }

    override fun encodeFormat(): MediaFormat {
        val outputSize = config.outputSize
        return MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, outputSize.width, outputSize.height)
            .apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
    }

    override fun onConfigure(encoder: MediaCodec) {
        surface = encoder.createInputSurface()
    }

    override fun finishEncoding() {
        try {
            VLog.d(TAG, "Starting video encoder finish process")

            // 发送EOS信号
            encoder.signalEndOfInputStream()
            VLog.d(TAG, "Video EOS signal sent")

            // 持续drain编码器直到收到EOS，确保所有数据都被处理
            onBufferEncodeCallback?.let { callback ->
                drainEncoderUntilEOS(callback)
            }

            VLog.d(TAG, "Video encoder finish process completed")
        } catch (e: Exception) {
            VLog.e(TAG, "Error in video EOS processing", e)
        }
    }

    fun getEncoderSurface(): Surface = surface!!

}