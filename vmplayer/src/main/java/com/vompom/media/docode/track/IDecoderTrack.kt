package com.vompom.media.docode.track

import com.vompom.media.model.SampleState
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/10/10 18:42
 *
 * @Description
 */

interface IDecoderTrack {
    fun setTrackSegments(segmentList: List<TrackSegment>)

    fun prepare()

    /**
     * 搜索指定位置的帧
     * @param targetUs 要搜索的时间位置（微秒）
     * @return 实际搜索到的位置（微秒）
     */
    fun seek(targetUs: Long): Long

    /**
     * 读取样本数据
     * @param playTimeUs 播放时间（微秒）
     * @return 样本状态
     */
    fun readSample(playTimeUs: Long): SampleState

    /**
     * 获取当前播放的时间位置
     * @return 当前播放位置（微秒）
     */
    fun playedUs(): Long

    /**
     * 释放资源
     */
    fun release()
}