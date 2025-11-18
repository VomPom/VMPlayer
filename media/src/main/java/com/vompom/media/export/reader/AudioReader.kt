package com.vompom.media.export.reader

import com.vompom.media.docode.track.AudioDecoderTrack
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/11/05 21:37
 *
 * @Description
 */

class AudioReader(segments: List<TrackSegment>) : BaseReader(segments) {
    private var audioDecoderTrack: AudioDecoderTrack? = null
    private var onAudioDataAvailable: ((ByteArray, Long) -> Unit)? = null

    val sampleDurationUs = 23220L // 约23ms per sample for 44.1kHz

    override fun prepare() {
        audioDecoderTrack = AudioDecoderTrack(segments, true).apply {
            setExportTimeInterval(sampleDurationUs)  // 设置精确的采样间隔
            prepare()
        }
    }

    fun setOnAudioDataAvailable(callback: (ByteArray, Long) -> Unit) {
        this.onAudioDataAvailable = callback
    }

    override fun onStart() {
        val durationUs = durationUs()
        while (isRunning() && readTimeUs < durationUs) {
            // 读取音频样本
            audioDecoderTrack?.readSample(readTimeUs)

            // 获取解码后的PCM数据
            val result = audioDecoderTrack?.getLastDecodedData()
            val decodedData = result?.first
            val bufferInfo = result?.second

            if (decodedData != null && bufferInfo != null) {
                // 将PCM数据转换为字节数组
                val audioData = ByteArray(decodedData.remaining())
                decodedData.get(audioData)

                // 在onBufferRead回调中处理音频数据
                onAudioDataAvailable?.invoke(audioData, bufferInfo.presentationTimeUs)
            }

            // 通知读取完成，触发编码器处理
            onBufferRead?.invoke(readTimeUs)
            readTimeUs += sampleDurationUs
        }
        onFinished?.invoke()
    }
}