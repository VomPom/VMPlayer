package com.vompom.media.docode.track

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 底层 PCM 混音引擎（无状态工具类）
 *
 * 核心算法：short 相加 + clamp（业界标准混音算法）
 * 适用于 16-bit signed PCM 数据
 */
object AudioMixer {

    /**
     * 两路 PCM 混合
     *
     * @param base 基准 PCM 数据（通常是原始音频）
     * @param overlay 叠加 PCM 数据（通常是 BGM）
     * @param baseVolume 基准音量（0.0 ~ 1.0）
     * @param overlayVolume 叠加音量（0.0 ~ 1.0）
     * @return 混合后的 PCM 数据
     */
    fun mergeSamples(
        base: ShortArray,
        overlay: ShortArray,
        baseVolume: Float = 1.0f,
        overlayVolume: Float = 1.0f
    ): ShortArray {
        val length = maxOf(base.size, overlay.size)
        val result = ShortArray(length)
        for (i in 0 until length) {
            val a = if (i < base.size) (base[i] * baseVolume).toInt() else 0
            val b = if (i < overlay.size) (overlay[i] * overlayVolume).toInt() else 0
            // clamp 到 Short 范围，防止溢出
            result[i] = (a + b).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    /**
     * 对 PCM 数据应用音量
     *
     * @param samples PCM 数据
     * @param volume 音量（0.0 ~ 1.0）
     * @return 应用音量后的 PCM 数据
     */
    fun applyVolume(samples: ShortArray, volume: Float): ShortArray {
        if (volume == 1.0f) return samples
        val result = ShortArray(samples.size)
        for (i in samples.indices) {
            result[i] = (samples[i] * volume).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return result
    }

    /**
     * ByteArray（16-bit PCM Little-Endian）转 ShortArray
     */
    fun pcmBytesToShortArray(pcmData: ByteArray): ShortArray {
        val shortArray = ShortArray(pcmData.size / 2)
        for (i in shortArray.indices) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            shortArray[i] = ((high shl 8) or low).toShort()
        }
        return shortArray
    }

    /**
     * ShortArray 转 ByteArray（16-bit PCM Little-Endian）
     */
    fun shortArrayToPcmBytes(shortArray: ShortArray): ByteArray {
        val byteArray = ByteArray(shortArray.size * 2)
        for (i in shortArray.indices) {
            val value = shortArray[i].toInt()
            byteArray[i * 2] = (value and 0xFF).toByte()
            byteArray[i * 2 + 1] = (value shr 8 and 0xFF).toByte()
        }
        return byteArray
    }
}
