package com.vompom.media.docode.track

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.model.TrackSegment
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/10/10 18:43
 *
 * @Description 负责音频轨道的数据管理
 * 管理共享的 AudioTrack 实例，避免片段切换时重新创建导致音频中断
 */

class AudioDecoderTrack() : BaseDecoderTrack() {
    companion object {
        private const val TAG = "AudioDecoderTrack"
    }

    /**
     * 持久化的 PCM 拦截器，会在每次创建新 decoder 时自动安装
     * 解决片段切换时拦截器丢失的问题
     */
    var persistentPcmInterceptor: ((ByteBuffer, MediaCodec.BufferInfo) -> ByteArray?)? = null

    /**
     * 共享的 AudioTrack 实例，在非导出模式下由 AudioDecoderTrack 管理生命周期
     * 片段切换时复用同一个 AudioTrack，避免音频中断
     */
    private var sharedAudioTrack: AudioTrack? = null

    constructor(segmentList: List<TrackSegment>, exportMode: Boolean = false) : this() {
        this.exportMode = exportMode
        setTrackSegments(segmentList)
        decodeType = IDecoder.DecodeType.Audio
    }

    override fun prepare() {
        super.prepare()
        // 非导出模式下，提前创建共享的 AudioTrack
        if (!exportMode) {
            initSharedAudioTrack()
        }
        nextSegment()
    }

    /**
     * 初始化共享的 AudioTrack
     * 使用第一个片段的音频格式参数
     */
    private fun initSharedAudioTrack() {
        if (segmentList.isEmpty()) return
        try {
            val firstSegment = segmentList[0]
            // 使用轻量的 AssetExtractor 获取音频格式，避免创建完整的 AudioDecoder
            val extractor = com.vompom.media.extractor.AssetExtractor().apply {
                setDataSource(firstSegment.asset.path)
                val audioTrackIndex = findTrack("audio/")
                selectTrack(audioTrackIndex)
            }
            val format = extractor.getMediaFormat()
            extractor.stop()

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
            val channel = if (channelCount == 1) {
                AudioFormat.CHANNEL_OUT_MONO
            } else {
                AudioFormat.CHANNEL_OUT_STEREO
            }
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channel, encoding)

            sharedAudioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(encoding)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .build()
            sharedAudioTrack?.play()
            VLog.d("$TAG: sharedAudioTrack created, sampleRate=$sampleRate, channels=$channelCount")
        } catch (e: Exception) {
            VLog.e("$TAG: failed to create sharedAudioTrack: ${e.message}")
            sharedAudioTrack = null
        }
    }

    override fun seek(targetUs: Long): Long {
        currentPlayUs = targetUs
        return super.seek(targetUs)
    }

    override fun createDecoder(segment: TrackSegment): IDecoder {
        val decoder = AudioDecoder(segment.asset)
        // 如果有共享的 AudioTrack，传递给 decoder（避免 decoder 自己创建）
        sharedAudioTrack?.let {
            decoder.setSharedAudioTrack(it)
        }
        decoder.start()
        // 如果有持久化拦截器，自动安装到新 decoder 上
        // 这确保片段切换时拦截器不会丢失
        persistentPcmInterceptor?.let {
            decoder.pcmInterceptor = it
        }
        return decoder
    }

    fun getLastDecodedData(): Pair<ByteBuffer?, MediaCodec.BufferInfo?> {
        val buffer = (currentDecoder as? AudioDecoder)?.getLastDecodedData()
        val bufferInfo = (currentDecoder as? AudioDecoder)?.getLastBufferInfo()
        return Pair(buffer, bufferInfo)
    }

    /**
     * 获取当前解码器实例（用于外部安装拦截器等）
     */
    fun getDecoder(): IDecoder? = currentDecoder

    override fun release() {
        super.release()
        // 释放共享的 AudioTrack
        try {
            sharedAudioTrack?.release()
        } catch (e: Exception) {
            VLog.e("$TAG: failed to release sharedAudioTrack: ${e.message}")
        }
        sharedAudioTrack = null
    }
}