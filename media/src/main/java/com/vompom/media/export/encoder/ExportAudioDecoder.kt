package com.vompom.media.export.encoder

import android.media.MediaCodec
import com.vompom.media.docode.decorder.BaseDecoder
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.docode.model.Asset
import com.vompom.media.docode.model.SampleState
import com.vompom.media.utils.VLog
import com.vompom.media.utils.usToS
import java.nio.ByteBuffer

/**
 * 专门用于导出的音频解码器
 *
 * 不直接播放音频，而是将解码后的PCM数据保存起来供编码器使用
 */
class ExportAudioDecoder(asset: Asset) : BaseDecoder(asset) {
    private var currentPts = 0L
    private var cnt = 0
    private var lastDecodedBuffer: ByteBuffer? = null
    private var lastBufferInfo: MediaCodec.BufferInfo? = null
    private var hasNewData = false // 标记是否有新数据

    override fun render(buffer: ByteBuffer?, bufferInfo: MediaCodec.BufferInfo) {
        if (buffer != null) {
            // 不播放音频，而是保存PCM数据
            lastDecodedBuffer = ByteBuffer.allocate(buffer.remaining()).apply {
                put(buffer)
                flip()
            }
            lastBufferInfo = MediaCodec.BufferInfo().apply {
                set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
            }
            hasNewData = true // 新数据已到达

            currentPts = bufferInfo.presentationTimeUs
            cnt++
            VLog.v("export audio pts:${usToS(bufferInfo.presentationTimeUs)}s size:${bufferInfo.size} offset: ${bufferInfo.offset} cnt: $cnt")
        }
    }

    override fun configure(codec: MediaCodec) {
        codec.configure(extractor.getMediaFormat(), null, null, 0)
    }

    override fun onPrepare() {
        // 不需要初始化AudioTrack
    }

    override fun readSample(targetTimeUs: Long): SampleState {
        if (isReleased) {
            return SampleState()
        }
        // 向 MediaCodec 添加解码的数据，在没有 EOS 之前一直添加
        if (!readSampleDone) {
            doReadSample()
        }

        // 从 MediaCodec 队列中获取解码后的数据
        if (!isDecodeDone) {
            return renderBuffer { true }
        }
        return SampleState()
    }

    override fun seek(timeUs: Long): Long {
        val ptsAfterSeek = extractor.seek(timeUs)
        currentPts = ptsAfterSeek
        return ptsAfterSeek
    }

    override fun currentPts(): Long = currentPts

    override fun decodeType(): IDecoder.DecodeType = IDecoder.DecodeType.Audio

    /**
     * 获取最后解码的PCM数据
     */
    fun getLastDecodedData(): ByteBuffer? {
        return if (hasNewData) {
            lastDecodedBuffer?.let { buffer ->
                // 创建一个新的ByteBuffer副本，避免数据被重复使用
                val copy = ByteBuffer.allocate(buffer.remaining())
                copy.put(buffer)
                copy.flip()

                // 重置原buffer的位置以便下次读取
                buffer.rewind()

                hasNewData = false // 数据已消费
                copy
            }
        } else {
            null
        }
    }

    /**
     * 获取最后的缓冲区信息
     */
    fun getLastBufferInfo(): MediaCodec.BufferInfo? {
        return lastBufferInfo
    }
}