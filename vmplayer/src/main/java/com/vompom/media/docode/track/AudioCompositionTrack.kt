package com.vompom.media.docode.track

import android.media.MediaCodec
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.SampleState
import com.vompom.media.model.TrackSegment
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 多轨道音频合成解码器，作为整个音频混合架构的核心枢纽
 *
 * 内部持有：
 * - Track 0（原始音频）：AudioDecoderTrack
 * - Track 1~N（BGM 等附加轨道）：AudioCompositor 列表
 *
 * 向后兼容：当 audioMixConfig 为 null 或没有附加轨道时，行为与原始 AudioDecoderTrack 完全一致
 *
 * 预览模式数据流：
 * ```
 * AudioDecoder.playFrame(buffer, bufferInfo)
 *   → pcmInterceptor(buffer, bufferInfo)
 *     ├── 将原始 PCM 转为 ShortArray
 *     ├── 逐条 compositor.readAndMix() 混合 BGM
 *     └── 返回混合后的 ByteArray → AudioTrack.write()
 * ```
 *
 * 导出模式数据流：
 * ```
 * readSample(playTimeUs)
 *   ├── originalTrack.readSample() → 获取原始 PCM
 *   ├── 逐条 compositor.readAndMix() 混合
 *   └── 缓存混合后的 PCM → AudioReader 读取
 * ```
 */
class AudioCompositionTrack : IDecoderTrack {

    companion object {
        private const val TAG = "AudioCompositionTrack"
    }

    private val originalTrack: AudioDecoderTrack
    private var audioMixConfig: AudioMixConfig?
    private val compositors = mutableListOf<AudioCompositor>()
    private val exportMode: Boolean

    // 混合后的 PCM 数据缓存（导出模式使用）
    private var lastMixedPcmData: ByteArray? = null
    private var lastMixedBufferInfo: MediaCodec.BufferInfo? = null
    private var hasMixedData = false

    // 当前播放时间（用于预览模式下 interceptor 获取时间）
    private var currentPlayTimeUs = 0L

    /**
     * 主构造函数
     *
     * @param segments 原始音频的轨道片段列表
     * @param audioMixConfig 多轨道混音配置，为 null 时退化为单轨道模式
     * @param exportMode 是否为导出模式
     */
    constructor(
        segments: List<TrackSegment>,
        audioMixConfig: AudioMixConfig? = null,
        exportMode: Boolean = false
    ) {
        this.audioMixConfig = audioMixConfig
        this.exportMode = exportMode
        this.originalTrack = AudioDecoderTrack(segments, exportMode)
    }

    override fun setTrackSegments(segmentList: List<TrackSegment>) {
        originalTrack.setTrackSegments(segmentList)
    }

    override fun prepare() {
        // 1. 准备原始音频轨道
        originalTrack.prepare()

        // 2. 如果有初始配置，准备合成器
        prepareCompositors()

        VLog.d("$TAG: prepared with ${compositors.size} extra tracks, exportMode=$exportMode")
    }

    /**
     * 动态更新音频混音配置（支持播放中调用）
     * 会释放旧的合成器并创建新的
     */
    fun updateAudioMixConfig(config: AudioMixConfig?) {
        // 释放旧的合成器
        releaseCompositors()
        this.audioMixConfig = config
        // 准备新的合成器
        prepareCompositors()
        // 安装或移除拦截器
        installInterceptorIfNeeded()
        VLog.d("$TAG: audioMixConfig updated, ${compositors.size} extra tracks")
    }

    /**
     * 清除音频混音配置，恢复原始音频
     */
    fun clearAudioMixConfig() {
        updateAudioMixConfig(null)
    }

    /**
     * 准备所有合成器
     */
    private fun prepareCompositors() {
        audioMixConfig?.trackInputs?.forEach { inputConfig ->
            try {
                val compositor = AudioCompositor(inputConfig, exportMode)
                compositor.prepare()
                compositors.add(compositor)
                VLog.d("$TAG: compositor prepared for trackId=${inputConfig.trackId}")
            } catch (e: Exception) {
                VLog.e("$TAG: failed to prepare compositor for trackId=${inputConfig.trackId}, error=${e.message}")
            }
        }
    }

    /**
     * 释放所有合成器
     */
    private fun releaseCompositors() {
        for (compositor in compositors) {
            compositor.release()
        }
        compositors.clear()
    }

    /**
     * 在预览模式下，安装 PCM 拦截器到原始音频轨道
     * 使用 persistentPcmInterceptor 确保片段切换时拦截器不会丢失
     */
    private fun installInterceptorIfNeeded() {
        if (exportMode) return // 导出模式不需要拦截器

        if (compositors.isNotEmpty()) {
            // 设置持久化拦截器：片段切换创建新 decoder 时会自动安装
            val interceptor: (ByteBuffer, MediaCodec.BufferInfo) -> ByteArray? = { buffer, bufferInfo ->
                mixPcmData(buffer, bufferInfo)
            }
            originalTrack.persistentPcmInterceptor = interceptor
            // 同时设置到当前 decoder 上（如果已存在）
            (originalTrack.getDecoder() as? AudioDecoder)?.pcmInterceptor = interceptor
            VLog.d("$TAG: persistentPcmInterceptor installed")
        } else {
            // 移除拦截器，恢复原始播放
            originalTrack.persistentPcmInterceptor = null
            (originalTrack.getDecoder() as? AudioDecoder)?.pcmInterceptor = null
            VLog.d("$TAG: persistentPcmInterceptor removed")
        }
    }

    /**
     * 将原始 PCM 数据与所有 BGM 轨道混合
     *
     * @param originalBuffer 原始 PCM ByteBuffer
     * @param bufferInfo 原始 BufferInfo
     * @return 混合后的 PCM ByteArray
     */
    private fun mixPcmData(originalBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo): ByteArray? {
        if (compositors.isEmpty()) return null

        try {
            // 将原始 PCM 转为 ShortArray
            val position = originalBuffer.position()
            val pcmBytes = ByteArray(originalBuffer.remaining())
            originalBuffer.get(pcmBytes)
            originalBuffer.position(position) // 恢复 position，不影响后续使用

            var mixedSamples = AudioMixer.pcmBytesToShortArray(pcmBytes)

            // 获取原始音频音量
            val originalVolume = audioMixConfig?.originalVolume ?: 1.0f

            // 逐条轨道混合
            for (compositor in compositors) {
                mixedSamples = compositor.readAndMix(mixedSamples, currentPlayTimeUs, originalVolume)
            }

            // 转回 ByteArray
            return AudioMixer.shortArrayToPcmBytes(mixedSamples)
        } catch (e: Exception) {
            VLog.e("$TAG: mixPcmData error: ${e.message}")
            return null
        }
    }

    override fun readSample(playTimeUs: Long): SampleState {
        // 更新当前播放时间（预览模式下 interceptor 需要用到）
        currentPlayTimeUs = playTimeUs

        // 预览模式下，确保持久化拦截器已安装
        if (!exportMode && compositors.isNotEmpty()) {
            if (originalTrack.persistentPcmInterceptor == null) {
                installInterceptorIfNeeded()
            }
        }

        // 1. 读取原始音频（预览模式下，拦截器会自动在 playFrame 中混合）
        //    即使片段切换创建了新 decoder，persistentPcmInterceptor 也会自动安装
        val state = originalTrack.readSample(playTimeUs)

        // 2. 导出模式下，需要手动混合并缓存
        if (exportMode && compositors.isNotEmpty()) {
            mixForExport(playTimeUs)
        } else if (exportMode) {
            cacheOriginalData()
        }

        return state
    }

    /**
     * 导出模式下的混音处理
     */
    private fun mixForExport(playTimeUs: Long) {
        val result = originalTrack.getLastDecodedData()
        val originalBuffer = result.first
        val originalBufferInfo = result.second

        if (originalBuffer == null || originalBufferInfo == null) return

        // 将原始 PCM 转为 ShortArray
        val pcmBytes = ByteArray(originalBuffer.remaining())
        originalBuffer.get(pcmBytes)
        var mixedSamples = AudioMixer.pcmBytesToShortArray(pcmBytes)

        val originalVolume = audioMixConfig?.originalVolume ?: 1.0f

        // 逐条轨道混合
        for (compositor in compositors) {
            mixedSamples = compositor.readAndMix(mixedSamples, playTimeUs, originalVolume)
        }

        // 转回 ByteArray 并缓存
        val mixedPcmBytes = AudioMixer.shortArrayToPcmBytes(mixedSamples)
        lastMixedPcmData = mixedPcmBytes
        lastMixedBufferInfo = MediaCodec.BufferInfo().apply {
            set(0, mixedPcmBytes.size, originalBufferInfo.presentationTimeUs, originalBufferInfo.flags)
        }
        hasMixedData = true
    }

    /**
     * 缓存原始音频数据（导出模式无混音时使用）
     */
    private fun cacheOriginalData() {
        val result = originalTrack.getLastDecodedData()
        val buffer = result.first
        val bufferInfo = result.second
        if (buffer != null && bufferInfo != null) {
            val pcmBytes = ByteArray(buffer.remaining())
            buffer.get(pcmBytes)
            lastMixedPcmData = pcmBytes
            lastMixedBufferInfo = MediaCodec.BufferInfo().apply {
                set(0, pcmBytes.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
            }
            hasMixedData = true
        }
    }

    /**
     * 获取混合后的 PCM 数据（导出模式使用）
     *
     * @return Pair<PCM数据, BufferInfo>
     */
    fun getMixedData(): Pair<ByteArray?, MediaCodec.BufferInfo?> {
        return if (hasMixedData) {
            hasMixedData = false
            Pair(lastMixedPcmData, lastMixedBufferInfo)
        } else {
            Pair(null, null)
        }
    }

    /**
     * 获取原始音频轨道的最后解码数据（向后兼容）
     */
    fun getLastDecodedData(): Pair<ByteBuffer?, MediaCodec.BufferInfo?> {
        return originalTrack.getLastDecodedData()
    }

    override fun seek(targetUs: Long): Long {
        // 1. Seek 原始音频轨道
        val result = originalTrack.seek(targetUs)

        // 2. 同步 Seek 所有附加轨道
        for (compositor in compositors) {
            compositor.seek(targetUs)
        }

        return result
    }

    override fun playedUs(): Long = originalTrack.playedUs()

    override fun release() {
        // 释放原始音频轨道
        originalTrack.release()

        // 释放所有合成器
        releaseCompositors()

        VLog.d("$TAG: released")
    }

    /**
     * 设置导出时间间隔（代理到原始轨道）
     */
    fun setExportTimeInterval(intervalUs: Long) {
        originalTrack.setExportTimeInterval(intervalUs)
    }

    /**
     * 是否有附加音频轨道
     */
    fun hasExtraTracks(): Boolean = compositors.isNotEmpty()
}