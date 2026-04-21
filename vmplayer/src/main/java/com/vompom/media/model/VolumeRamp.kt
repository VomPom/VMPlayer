package com.vompom.media.model

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 音量渐变区间定义，用于实现淡入淡出效果
 *
 * @param startTimeUs 渐变开始时间（微秒）
 * @param endTimeUs 渐变结束时间（微秒）
 * @param startVolume 渐变开始时的音量（0.0 ~ 1.0）
 * @param endVolume 渐变结束时的音量（0.0 ~ 1.0）
 */
data class VolumeRamp(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val startVolume: Float,
    val endVolume: Float
) {
    init {
        require(startTimeUs <= endTimeUs) { "startTimeUs must <= endTimeUs" }
        require(startVolume in 0f..1f) { "startVolume must be in [0, 1]" }
        require(endVolume in 0f..1f) { "endVolume must be in [0, 1]" }
    }

    /**
     * 根据当前时间计算插值后的音量
     *
     * @param timeUs 当前播放时间（微秒）
     * @return 插值后的音量值
     */
    fun getVolumeAtTime(timeUs: Long): Float {
        if (timeUs <= startTimeUs) return startVolume
        if (timeUs >= endTimeUs) return endVolume
        // 线性插值
        val progress = (timeUs - startTimeUs).toFloat() / (endTimeUs - startTimeUs).toFloat()
        return startVolume + (endVolume - startVolume) * progress
    }

    /**
     * 判断给定时间是否在此渐变区间内
     */
    fun contains(timeUs: Long): Boolean = timeUs in startTimeUs..endTimeUs
}
