package com.vompom.media.docode.decorder

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.vompom.media.model.Asset
import com.vompom.media.model.SampleState
import com.vompom.media.utils.VLog
import com.vompom.media.utils.usToS
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/09/24 21:43
 *
 * @Description
 */

class AudioDecoder(asset: Asset) : BaseDecoder(asset) {
    private lateinit var audioTrack: AudioTrack
    private var currentPts = 0L
    private var cnt = 0


    private var lastDecodedBuffer: ByteBuffer? = null
    private var lastBufferInfo: MediaCodec.BufferInfo? = null
    private var hasNewData = false // 标记是否有新数据

    override fun render(buffer: ByteBuffer?, bufferInfo: MediaCodec.BufferInfo) {
        if (buffer != null) {
            if (isExportMode()) {
                exportFrame(buffer, bufferInfo)
            } else {
                playFrame(buffer, bufferInfo)
            }
        }
    }

    private fun playFrame(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        lastDecodedBuffer = buffer
        lastBufferInfo = bufferInfo
        audioTrack.write(buffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
        currentPts = bufferInfo.presentationTimeUs
        cnt++
        VLog.v("audio pts:${usToS(bufferInfo.presentationTimeUs)}s size:${bufferInfo.size} offset: ${bufferInfo.offset} cnt: $cnt")
    }

    private fun exportFrame(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
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
    }

    override fun configure(codec: MediaCodec) {
        codec.configure(extractor.getMediaFormat(), null, null, 0)
    }

    override fun onPrepare() {
        if (isExportMode()) {

        } else {
            initAudioPlayer()
        }
    }

    private fun initAudioPlayer() {
        val format = extractor.getMediaFormat()
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }

        val channel = if (channelCount == 1) {
            AudioFormat.CHANNEL_OUT_MONO    // 单声道
        } else {
            AudioFormat.CHANNEL_OUT_STEREO  // 双声道
        }

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channel, encoding)

        audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat
                    .Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .build()
        audioTrack.play()
    }

    override fun readSample(targetTimeUs: Long): SampleState {
        if (isReleased) {
            return SampleState()
        }
        if (isNeedSeek(targetTimeUs)) {
            seek(targetTimeUs)
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

    override fun release() {
        super.release()
        audioTrack.release()
    }

    override fun decodeType(): IDecoder.DecodeType = IDecoder.DecodeType.Audio

    /**
     * 判断是否需要 seek 一次，视频是不停地轮询直到获取到目标时间，音频只需要 seek 一次即可
     *
     * @return
     */
    private fun isNeedSeek(targetUs: Long): Boolean {
        return lastBufferInfo == null && targetUs > 0
    }

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