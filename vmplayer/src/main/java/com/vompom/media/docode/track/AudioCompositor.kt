package com.vompom.media.docode.track

import com.vompom.media.model.AudioTrackInputConfig
import com.vompom.media.model.ClipAsset
import com.vompom.media.model.TimeRange
import com.vompom.media.model.TrackSegment
import com.vompom.media.utils.VLog

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 单轨合成器，负责管理一条附加音频轨道（如 BGM）的解码和混音
 *
 * 每个 AudioCompositor 内部持有一个 AudioDecoderTrack，负责：
 * 1. 解码 BGM 文件的 PCM 数据
 * 2. 根据 AudioTrackInputConfig 的配置（音量、循环、淡入淡出）处理 PCM
 * 3. 将处理后的 PCM 与基准 PCM 混合
 */
class AudioCompositor(
    private val inputConfig: AudioTrackInputConfig,
    private val exportMode: Boolean = false
) {
    companion object {
        private const val TAG = "AudioCompositor"
    }

    private var decoderTrack: AudioDecoderTrack? = null
    private var isPrepared = false
    private var isReleased = false

    /**
     * 准备合成器，初始化内部的 AudioDecoderTrack
     */
    fun prepare() {
        if (isPrepared || isReleased) return
        try {
            // 使用一个足够大但不会溢出的 durationUs，ClipAsset.checkRange() 会自动裁剪到实际时长
            val asset = ClipAsset(inputConfig.filePath, TimeRange(0L, Long.MAX_VALUE / 2))
            val segment = TrackSegment(asset).apply {
                // BGM 轨道的 timelineRange 从 startOffsetUs 开始
                timelineRange.updateStartUs(0L)
            }
            // BGM 轨道的解码器始终使用导出模式（不初始化 AudioTrack），
            // 因为 BGM 的 PCM 数据只用于混合到原始音频中，不应该自己独立播放
            decoderTrack = AudioDecoderTrack(listOf(segment), true).apply {
                prepare()
            }
            isPrepared = true
            VLog.d("$TAG: prepared trackId=${inputConfig.trackId}, file=${inputConfig.filePath}")
        } catch (e: Exception) {
            VLog.e("$TAG: failed to prepare trackId=${inputConfig.trackId}, error=${e.message}")
            isPrepared = false
        }
    }

    /**
     * 读取当前轨道的 PCM 数据并与基准 PCM 混合
     *
     * @param baseSamples 基准 PCM 数据（ShortArray）
     * @param playTimeUs 当前播放时间（微秒）
     * @param baseVolume 基准音频的音量
     * @return 混合后的 PCM 数据
     */
    fun readAndMix(baseSamples: ShortArray, playTimeUs: Long, baseVolume: Float): ShortArray {
        if (!isPrepared || isReleased || decoderTrack == null) {
            return baseSamples
        }

        // 如果当前时间还没到 BGM 的起始偏移时间，不混合
        if (playTimeUs < inputConfig.startOffsetUs) {
            return baseSamples
        }

        // 计算 BGM 轨道内部的播放时间
        val bgmTimeUs = playTimeUs - inputConfig.startOffsetUs

        try {
            // 读取 BGM 的 PCM 数据
            val state = decoderTrack!!.readSample(bgmTimeUs)

            // 如果 BGM 播放结束
            if (state.statusCode < 0) {
                if (inputConfig.loop) {
                    // 循环模式：seek 回起点重新播放
                    decoderTrack!!.seek(0L)
                    decoderTrack!!.readSample(0L)
                } else {
                    // 非循环模式：不混合，直接返回基准数据
                    return baseSamples
                }
            }

            // 获取解码后的 PCM 数据
            val result = decoderTrack!!.getLastDecodedData()
            val decodedBuffer = result.first ?: return baseSamples

            // 将 ByteBuffer 转为 ByteArray 再转为 ShortArray
            val pcmBytes = ByteArray(decodedBuffer.remaining())
            decodedBuffer.get(pcmBytes)
            val overlaySamples = AudioMixer.pcmBytesToShortArray(pcmBytes)

            // 获取当前时刻的音量（考虑淡入淡出）
            val overlayVolume = inputConfig.getVolumeAtTime(playTimeUs)

            // 混合
            return AudioMixer.mergeSamples(baseSamples, overlaySamples, baseVolume, overlayVolume)
        } catch (e: Exception) {
            VLog.e("$TAG: readAndMix error for trackId=${inputConfig.trackId}, error=${e.message}")
            return baseSamples
        }
    }

    /**
     * Seek 到指定位置
     *
     * @param targetUs 目标时间（微秒），相对于整个时间轴
     */
    fun seek(targetUs: Long) {
        if (!isPrepared || isReleased) return
        val bgmTimeUs = maxOf(0L, targetUs - inputConfig.startOffsetUs)
        try {
            decoderTrack?.seek(bgmTimeUs)
        } catch (e: Exception) {
            VLog.e("$TAG: seek error for trackId=${inputConfig.trackId}, error=${e.message}")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        if (isReleased) return
        isReleased = true
        isPrepared = false
        try {
            decoderTrack?.release()
        } catch (e: Exception) {
            VLog.e("$TAG: release error for trackId=${inputConfig.trackId}, error=${e.message}")
        }
        decoderTrack = null
    }
}
