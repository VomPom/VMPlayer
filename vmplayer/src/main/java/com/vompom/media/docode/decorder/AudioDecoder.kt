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
    private var audioTrack: AudioTrack? = null
    private var ownsAudioTrack = true  // 是否由自己管理 AudioTrack 的生命周期
    private var currentPts = 0L
    private var cnt = 0


    private var lastDecodedBuffer: ByteBuffer? = null
    private var lastBufferInfo: MediaCodec.BufferInfo? = null
    private var hasNewData = false // 标记是否有新数据

    /**
     * PCM 数据拦截器：外部可以设置此回调来替换即将写入 AudioTrack 的 PCM 数据
     * 参数：原始 PCM ByteBuffer 和 BufferInfo
     * 返回：替换后的 PCM ByteArray，返回 null 则使用原始数据
     */
    var pcmInterceptor: ((ByteBuffer, MediaCodec.BufferInfo) -> ByteArray?)? = null

    /**
     * 设置外部共享的 AudioTrack，避免片段切换时重新创建
     * 调用此方法后，AudioDecoder 不再管理 AudioTrack 的生命周期
     */
    fun setSharedAudioTrack(sharedAudioTrack: AudioTrack) {
        this.audioTrack = sharedAudioTrack
        this.ownsAudioTrack = false
    }

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
        hasNewData = true

        val track = audioTrack ?: return

        // 如果有拦截器，先让拦截器处理（用于混音场景）
        val intercepted = pcmInterceptor?.invoke(buffer, bufferInfo)
        if (intercepted != null) {
            // 使用混合后的 PCM 数据播放
            track.write(intercepted, 0, intercepted.size, AudioTrack.WRITE_BLOCKING)
        } else {
            // 使用原始 PCM 数据播放
            track.write(buffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
        }

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
            // 导出模式不需要 AudioTrack
        } else if (audioTrack == null) {
            // 只有没有外部共享 AudioTrack 时才自己创建
            initAudioPlayer()
            ownsAudioTrack = true
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
        audioTrack?.play()
    }

    companion object {
        /**
         * 音频解码重试最大次数，避免无限循环
         * 正常情况下 MediaCodec 在喂入 2-3 帧后就能产出输出，设置为 30 次作为安全上限
         */
        private const val MAX_RETRY_COUNT = 30
    }

    /**
     * 音频 readSample 重写：加入 while 循环重试机制
     *
     * 与视频不同，音频对连续性要求极高，任何一帧的缺失都会导致听觉上的"卡顿"或"断流"。
     * 在片段切换后，新的 MediaCodec 刚初始化时，前几次 dequeueOutputBuffer 必然返回
     * INFO_TRY_AGAIN_LATER（因为解码管线还没有填满），如果像视频那样直接返回 FAILED，
     * AudioTrack 就会因为没有数据写入而产生静音间隙。
     *
     * 因此这里采用 while 循环：持续喂数据 + 取数据，直到拿到有效的 PCM 帧或达到超时上限。
     */
    override fun readSample(targetTimeUs: Long): SampleState {
        if (isReleased) {
            return SampleState()
        }
        if (isNeedSeek(targetTimeUs)) {
            seek(targetTimeUs)
        }

        var retryCount = 0
        while (!isReleased && !isDecodeDone) {
            // 向 MediaCodec 添加解码的数据，在没有 EOS 之前一直添加
            if (!isReadSampleDone) {
                doReadSample()
            }

            // 从 MediaCodec 队列中获取解码后的数据
            val state = renderBuffer { true }

            // 成功拿到有效帧、解码完成、或遇到不可恢复的错误，直接返回
            if (state.statusCode == IDecoder.SAMPLE_STATE_NORMAL
                || state.statusCode == IDecoder.SAMPLE_STATE_FINISH
                || state.statusCode == IDecoder.SAMPLE_STATE_ERROR
            ) {
                return state
            }

            // SAMPLE_STATE_FAILED（即 TRY_AGAIN_LATER）：继续重试
            retryCount++
            if (retryCount >= MAX_RETRY_COUNT) {
                VLog.w("AudioDecoder readSample retry exhausted after $MAX_RETRY_COUNT attempts")
                return state
            }
        }
        return SampleState()
    }

    /**
     * 音频解码器的片段重置：在 BaseDecoder.resetForNewSegment 基础上，
     * 额外重置音频特有的状态（PTS、计数器、缓冲区等）
     *
     * 优化：音频解码不使用 mirrorExtractor，默认跳过其重置以减少文件 I/O 开销
     */
    override fun resetForNewSegment(newSourcePath: String, skipMirrorExtractor: Boolean): Boolean {
        val success = super.resetForNewSegment(newSourcePath, skipMirrorExtractor = true)
        if (success) {
            // 重置音频特有状态
            currentPts = 0L
            cnt = 0
            lastDecodedBuffer = null
            lastBufferInfo = null
            hasNewData = false
        }
        return success
    }

    override fun seek(timeUs: Long): Long {
        val ptsAfterSeek = extractor.seek(timeUs)
        currentPts = ptsAfterSeek
        return ptsAfterSeek
    }

    override fun currentPts(): Long = currentPts

    override fun release() {
        super.release()
        // 只有自己创建的 AudioTrack 才由自己释放
        if (ownsAudioTrack && audioTrack != null) {
            audioTrack?.release()
        }
        audioTrack = null
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