package com.vompom.media.export.reader

import com.vompom.media.docode.model.TrackSegment
import com.vompom.media.export.encoder.ExportAudioDecoderTrack

/**
 *
 * Created by @juliswang on 2025/11/05 21:37
 *
 * @Description
 */

class AudioReader(segments: List<TrackSegment>) : BaseReader(segments) {
    private var audioDecoderTrack: ExportAudioDecoderTrack? = null

    val sampleDurationUs = 23220L // 约23ms per sample for 44.1kHz

    override fun prepare() {
        audioDecoderTrack = ExportAudioDecoderTrack(segments).apply {
            prepare()
        }
    }
    override fun start() {
        val durationUs = durationUs()
        while (isRunning() && readTimeUs < durationUs) {
            audioDecoderTrack?.readSample(readTimeUs)
            onBufferRead?.invoke(readTimeUs)
            readTimeUs += sampleDurationUs
        }
        onFinished?.invoke()
    }

}