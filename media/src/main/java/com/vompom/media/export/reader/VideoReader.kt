package com.vompom.media.export.reader

import android.view.Surface
import com.vompom.media.docode.track.VideoDecoderTrack
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/11/05 21:37
 *
 * @Description
 */

class VideoReader(segments: List<TrackSegment>, val surface: Surface, fps: Int) : BaseReader(segments) {
    private var videoDecoderTrack: VideoDecoderTrack? = null

    private val frameDuration: Long = (1000_000 / fps).toLong()


    override fun prepare() {
        videoDecoderTrack = VideoDecoderTrack(segments, surface, true).apply {
            setExportTimeInterval(frameDuration)
            prepare()
        }
    }

    override fun onStart() {
        val durationUs = durationUs()
        while (isRunning() && readTimeUs < durationUs) {
            videoDecoderTrack?.readSample(readTimeUs)
            onBufferRead?.invoke(readTimeUs)
            readTimeUs += frameDuration
        }
        onFinished?.invoke()
    }

}