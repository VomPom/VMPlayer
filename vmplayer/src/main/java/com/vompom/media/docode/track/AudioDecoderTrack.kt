package com.vompom.media.docode.track

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.extractor.AssetExtractor
import com.vompom.media.model.SampleState
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

    /**
     * 预缓存每个片段的音频 MIME type，避免每次片段切换时创建临时 AssetExtractor 做文件 I/O
     * key: segmentList 中的索引, value: MIME type 字符串
     */
    private val segmentMimeCache = mutableMapOf<Int, String?>()

    constructor(segmentList: List<TrackSegment>, exportMode: Boolean = false) : this() {
        this.exportMode = exportMode
        setTrackSegments(segmentList)
        decodeType = IDecoder.DecodeType.Audio
    }

    override fun prepare() {
        super.prepare()
        // 预缓存所有片段的 MIME type，避免片段切换时的文件 I/O 开销
        preloadSegmentMimeTypes()
        // 非导出模式下，提前创建共享的 AudioTrack
        if (!exportMode) {
            initSharedAudioTrack()
        }
        nextSegment()
    }

    /**
     * 预加载所有片段的音频 MIME type 并缓存
     * 在 prepare 阶段一次性完成，避免每次 switchToNextSegment 时重复创建 AssetExtractor
     */
    private fun preloadSegmentMimeTypes() {
        segmentMimeCache.clear()
        segmentList.forEachIndexed { index, segment ->
            try {
                val tempExtractor = AssetExtractor().apply {
                    setDataSource(segment.asset.path)
                    val audioTrackIndex = findTrack("audio/")
                    selectTrack(audioTrackIndex)
                }
                val mime = tempExtractor.getMediaFormat().getString(MediaFormat.KEY_MIME)
                tempExtractor.stop()
                segmentMimeCache[index] = mime
            } catch (e: Exception) {
                VLog.e("$TAG: failed to preload mime for segment $index: ${e.message}")
                segmentMimeCache[index] = null
            }
        }
        VLog.d("$TAG: preloaded ${segmentMimeCache.size} segment MIME types")
    }

    /**
     * 初始化共享的 AudioTrack
     * 使用第一个片段的音频格式参数
     * todo:: 使用一个配置的音频格式数据非第一个
     */
    private fun initSharedAudioTrack() {
        if (segmentList.isEmpty()) return
        try {
            val firstSegment = segmentList[0]
            // 使用轻量的 AssetExtractor 获取音频格式，避免创建完整的 AudioDecoder
            val extractor = AssetExtractor().apply {
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

    /**
     * 重写 readSample，在片段切换时优先尝试复用 MediaCodec（flush 模式）
     *
     * 与 BaseDecoderTrack.readSample 的区别：
     * 当检测到需要切换到下一个片段时，不直接调用 nextSegment()（会 release + recreate MediaCodec），
     * 而是先判断新旧片段的音频编码格式是否相同：
     *   - 相同：调用 resetForNewSegment() 复用 MediaCodec，仅做 flush + 重置 Extractor（1-5ms）
     *   - 不同：走原来的 nextSegment() 路径，完整重建 MediaCodec（30-80ms）
     */
    override fun readSample(playTimeUs: Long): SampleState {
        if (isNeedDecodeNext(playTimeUs)) {
            switchToNextSegment()
        }
        val readSampleTimeUs = calSegmentSampleTime(playTimeUs)
        val state = currentDecoder!!.readSample(readSampleTimeUs)
        updateCurrentPlayUs(state.frameTimeUs)
        return state
    }

    /**
     * 音频片段切换：优先复用 MediaCodec，降低切换延迟
     *
     * 当新旧片段的音频 MIME type 相同时，只需 flush MediaCodec + 重新设置 Extractor 数据源，
     * 避免耗时的 release + createDecoderByType + configure + start 流程。
     */
    private fun switchToNextSegment() {
        val nextIndex = if (currentSegmentIndex + 1 < segmentList.size) {
            currentSegmentIndex + 1
        } else {
            0
        }
        val nextSegment = segmentList[nextIndex]

        // 尝试复用当前 MediaCodec（使用预缓存的 MIME type，无文件 I/O 开销）
        val canReuse = canReuseDecoder(nextIndex)
        if (canReuse) {
            // 复用模式：flush MediaCodec + 重置 Extractor
            val audioDecoder = currentDecoder as AudioDecoder
            val success: Boolean
            synchronized(decoderLock) {
                success = audioDecoder.resetForNewSegment(nextSegment.asset.path)
            }
            if (success) {
                currentSegmentIndex = nextIndex
                // seek 到新片段的起始位置
                currentDecoder?.seek(currentSegment().sourceRange.startUs)
                VLog.d("$TAG: reused MediaCodec for segment $currentSegmentIndex (flush mode)")
                return
            }
            // resetForNewSegment 失败，降级到完整重建路径
            VLog.e("$TAG: resetForNewSegment failed, falling back to full recreate")
        } else {
            VLog.d("$TAG: cannot reuse MediaCodec, recreating for segment $nextIndex")
        }
        // 降级：走完整重建路径，但使用异步释放旧 decoder 减少阻塞
        nextSegmentAsync()
    }

    /**
     * 音频专用的完整重建路径：异步释放旧 decoder，减少音频线程阻塞时间
     * 参考 TAVFoundation 的 ThreadPool.execute() 异步释放模式
     */
    private fun nextSegmentAsync() {
        if (currentSegmentIndex + 1 < segmentList.size) {
            currentSegmentIndex++
        } else {
            currentSegmentIndex = 0
        }
        doCreateDecoder(asyncRelease = true)
        currentDecoder?.seek(currentSegment().sourceRange.startUs)
    }

    /**
     * 判断是否可以复用当前的 MediaCodec 来解码新片段
     *
     * 复用条件：
     * 1. 当前 decoder 存在且是 AudioDecoder
     * 2. 新旧片段的音频 MIME type 相同（如都是 audio/mp4a-latm）
     *
     * 优化：使用预缓存的 MIME type，避免每次切换时创建临时 AssetExtractor 做文件 I/O
     *
     * @param nextSegmentIndex 即将切换到的新片段索引
     * @return true 可以复用，false 需要重建
     */
    private fun canReuseDecoder(nextSegmentIndex: Int): Boolean {
        val decoder = currentDecoder as? AudioDecoder ?: return false
        val currentMime = segmentMimeCache[currentSegmentIndex] ?: return false
        val nextMime = segmentMimeCache[nextSegmentIndex] ?: return false
        return currentMime == nextMime
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