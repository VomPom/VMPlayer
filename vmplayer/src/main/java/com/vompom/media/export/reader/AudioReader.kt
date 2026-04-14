package com.vompom.media.export.reader

import com.vompom.media.docode.track.AudioCompositionTrack
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/11/05 21:37
 *
 * @Description 导出时的音频读取器
 * 使用 AudioCompositionTrack 支持多轨道混音
 */

class AudioReader(
    segments: List<TrackSegment>,
    private val audioMixConfig: AudioMixConfig? = null
) : BaseReader(segments) {
    private var audioCompositionTrack: AudioCompositionTrack? = null
    private var onAudioDataAvailable: ((ByteArray, Long) -> Unit)? = null

    val sampleDurationUs = 23220L // 约23ms per sample for 44.1kHz

    override fun prepare() {
        audioCompositionTrack = AudioCompositionTrack(segments, audioMixConfig, exportMode = true).apply {
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
            // 读取音频样本（内部会自动进行多轨道混音）
            audioCompositionTrack?.readSample(readTimeUs)

            if (audioCompositionTrack?.hasExtraTracks() == true) {
                // 有附加轨道：使用混合后的数据
                val mixedResult = audioCompositionTrack?.getMixedData()
                val mixedData = mixedResult?.first
                val bufferInfo = mixedResult?.second

                if (mixedData != null && bufferInfo != null) {
                    onAudioDataAvailable?.invoke(mixedData, bufferInfo.presentationTimeUs)
                }
            } else {
                // 无附加轨道：使用原始数据（向后兼容）
                val result = audioCompositionTrack?.getLastDecodedData()
                val decodedData = result?.first
                val bufferInfo = result?.second

                if (decodedData != null && bufferInfo != null) {
                    val audioData = ByteArray(decodedData.remaining())
                    decodedData.get(audioData)
                    onAudioDataAvailable?.invoke(audioData, bufferInfo.presentationTimeUs)
                }
            }

            // 通知读取完成，触发编码器处理
            onBufferRead?.invoke(readTimeUs)
            readTimeUs += sampleDurationUs
        }
        onFinished?.invoke()
    }
}