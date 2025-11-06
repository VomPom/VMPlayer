package com.vompom.media.export.writer

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.vompom.media.export.EncodeManager.ExportConfig
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/05 16:59
 *
 * @Description
 */

class AudioEncoder(val config: ExportConfig) : BaseEncoder() {

    companion object {
        private const val TAG = "AudioEncoder"
    }

    override fun encodeType(): String {
        return AUDIO_MIME_TYPE
    }

    override fun encodeFormat(): MediaFormat {
        return MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, config.audioSampleRate, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
    }

    override fun onConfigure(encoder: MediaCodec) {
        //no-ops
    }

    override fun finishEncoding() {
        try {
            VLog.d(TAG, "Starting audio encoder finish process")

            // 发送音频EOS信号
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(
                    inputBufferIndex, 0, 0, 0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                VLog.d(TAG, "Audio EOS signal sent")
            } else {
                VLog.w(TAG, "Failed to get input buffer for EOS signal")
            }

            // 持续drain编码器直到收到EOS，确保所有数据都被处理
            onBufferEncodeCallback?.let { callback ->
                drainEncoderUntilEOS(callback)
            }

            VLog.d(TAG, "Audio encoder finish process completed")
        } catch (e: Exception) {
            VLog.e(TAG, "Error in audio EOS processing", e)
        }
    }

    /**
     * 编码音频数据
     */
    fun encodeAudioData(audioData: ByteBuffer, presentationTimeUs: Long) {
        if (audioData.remaining() <= 0) {
            VLog.w(TAG, "Empty audio data, skip")
            return
        }

        try {
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.let { buffer ->
                    buffer.clear()

                    val dataSize = audioData.remaining()
                    val bufferCapacity = buffer.capacity()

                    if (dataSize <= bufferCapacity) {
                        buffer.put(audioData)
                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            dataSize,
                            presentationTimeUs,
                            0
                        )
                        VLog.d(TAG, "Successfully queued audio data: size=$dataSize, pts=$presentationTimeUs")
                    } else {
                        VLog.w(TAG, "Audio data too large ($dataSize > $bufferCapacity), truncating")
                        val originalLimit = audioData.limit()
                        audioData.limit(audioData.position() + bufferCapacity)
                        buffer.put(audioData)
                        audioData.limit(originalLimit)

                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            bufferCapacity,
                            presentationTimeUs,
                            0
                        )
                    }
                }
            } else {
                VLog.w(TAG, "No input buffer available for audio data")
            }
        } catch (e: Exception) {
            VLog.e(TAG, "Error encoding audio data", e)
        }
    }
}