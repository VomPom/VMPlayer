package com.vompom.media.docode.track

import com.vompom.media.docode.model.SampleState
import com.vompom.media.docode.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/10/10 18:42
 *
 * @Description
 */

interface IDecoderTrack {
    fun prepare()
    fun setTrackSegments(segmentList: List<TrackSegment>)
    fun readSample(playTimeUs: Long): SampleState
    fun seek(targetUs: Long): Long
    fun release()
    fun playedUs(): Long
}