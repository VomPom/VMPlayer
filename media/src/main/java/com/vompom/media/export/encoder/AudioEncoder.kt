package com.vompom.media.export.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.vompom.media.export.ExportManager.ExportConfig
import com.vompom.media.utils.VLog

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

    /**
     * 将PCM音频数据传递给编码器
     *
     * @param audioData             原始的 PCM 数据
     * @param presentationTimeUs
     */
    fun encodeAudioData(audioData: ByteArray, presentationTimeUs: Long) {
        try {
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.let { buffer ->
                    buffer.clear()

                    if (audioData.size <= buffer.capacity()) {
                        buffer.put(audioData)
                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            audioData.size,
                            presentationTimeUs,
                            0
                        )
                        VLog.d(TAG, "Audio data queued: size=${audioData.size}, pts=$presentationTimeUs")

                        // 不需要手动调用drain，BaseEncoder的listen方法会处理
                        // 这样避免了重复调用onBufferEncode
                    } else {
                        VLog.w(TAG, "Audio data too large: ${audioData.size} > ${buffer.capacity()}")
                    }
                }
            } else {
                VLog.w(TAG, "No audio input buffer available")
            }
        } catch (e: Exception) {
            VLog.e(TAG, "Error encoding audio data: ${e.message}")
        }
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
            VLog.e(TAG, "Error in audio EOS processing: ${e.message}")
        }
    }


}