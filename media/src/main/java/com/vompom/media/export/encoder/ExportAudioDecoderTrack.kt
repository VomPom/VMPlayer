package com.vompom.media.export.encoder

import android.media.MediaCodec
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.docode.model.TrackSegment
import com.vompom.media.docode.track.BaseDecoderTrack
import java.nio.ByteBuffer

/**
 * 专门用于导出的音频解码轨道
 *
 * 使用ExportAudioDecoder来获取原始PCM数据，而不是直接播放
 */
class ExportAudioDecoderTrack(segmentList: List<TrackSegment>) : BaseDecoderTrack() {

    init {
        super.setTrackSegments(segmentList)
        decodeType = IDecoder.DecodeType.Audio
    }

    override fun prepare() {
        nextSegment()
    }

    override fun seek(targetUs: Long): Long {
        currentPlayUs = targetUs
        return super.seek(targetUs)
    }

    override fun createDecoder(segment: TrackSegment): IDecoder {
        val decoder = ExportAudioDecoder(segment.asset)
        decoder.start()
        return decoder
    }

    /**
     * 获取最后解码的PCM数据
     */
    fun getLastDecodedData(): ByteBuffer? {
        return (currentDecoder as? ExportAudioDecoder)?.getLastDecodedData()
    }

    /**
     * 获取最后的缓冲区信息
     */
    fun getLastBufferInfo(): MediaCodec.BufferInfo? {
        return (currentDecoder as? ExportAudioDecoder)?.getLastBufferInfo()
    }
}