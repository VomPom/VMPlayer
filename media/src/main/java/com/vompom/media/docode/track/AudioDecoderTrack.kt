package com.vompom.media.docode.track

import android.media.MediaCodec
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.model.TrackSegment
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/10/10 18:43
 *
 * @Description 负责音频轨道的数据管理
 */

class AudioDecoderTrack() : BaseDecoderTrack() {
    constructor(segmentList: List<TrackSegment>, exportMode: Boolean = false) : this() {
        this.exportMode = exportMode
        setTrackSegments(segmentList)
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
        val decoder = AudioDecoder(segment.asset)
        decoder.start()
        decoder.setExportMode(exportMode)
        return decoder
    }

    fun getLastDecodedData(): Pair<ByteBuffer?, MediaCodec.BufferInfo?> {
        val buffer = (currentDecoder as? AudioDecoder)?.getLastDecodedData()
        val bufferInfo = (currentDecoder as? AudioDecoder)?.getLastBufferInfo()
        return Pair(buffer, bufferInfo)
    }
}