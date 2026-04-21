package com.vompom.media.model

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 单条音频轨道的输入配置
 *
 * @param trackId 轨道唯一标识
 * @param filePath 音频文件路径
 * @param volume 基础音量（0.0 ~ 1.0），默认 1.0
 * @param loop 是否循环播放，默认 true
 * @param startOffsetUs 在时间轴上的起始偏移量（微秒），默认 0（从头开始）
 * @param volumeRamps 音量渐变区间列表，用于淡入淡出
 */
data class AudioTrackInputConfig(
    val trackId: Int,
    val filePath: String,
    val volume: Float = 1.0f,
    val loop: Boolean = true,
    val startOffsetUs: Long = 0L,
    val volumeRamps: List<VolumeRamp> = emptyList()
) {
    init {
        require(volume in 0f..1f) { "volume must be in [0, 1]" }
        require(startOffsetUs >= 0) { "startOffsetUs must >= 0" }
    }

    /**
     * 根据当前播放时间获取实际音量
     * 优先使用 VolumeRamp 渐变值，若不在任何渐变区间内则返回基础音量
     *
     * @param timeUs 当前播放时间（微秒）
     * @return 当前时刻的音量值
     */
    fun getVolumeAtTime(timeUs: Long): Float {
        // 查找当前时间所在的渐变区间
        for (ramp in volumeRamps) {
            if (ramp.contains(timeUs)) {
                return ramp.getVolumeAtTime(timeUs)
            }
        }
        return volume
    }
}
