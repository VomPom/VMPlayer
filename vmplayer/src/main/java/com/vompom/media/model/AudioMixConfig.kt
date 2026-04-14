package com.vompom.media.model

/**
 * Created by @juliswang on 2026/04/14
 *
 * @Description 多轨道音频混音配置，持有所有音频轨道的输入参数
 *
 * 使用示例：
 * ```
 * val config = AudioMixConfig()
 * config.addTrack(AudioTrackInputConfig(
 *     trackId = 1,
 *     filePath = "/path/to/bgm.mp3",
 *     volume = 0.5f,
 *     loop = true
 * ))
 * player.setAudioMix(config)
 * ```
 */
class AudioMixConfig {
    /**
     * 原始音频轨道的音量（0.0 ~ 1.0），默认 1.0
     */
    var originalVolume: Float = 1.0f
        set(value) {
            require(value in 0f..1f) { "originalVolume must be in [0, 1]" }
            field = value
        }

    /**
     * 所有附加音频轨道的配置列表
     */
    private val _trackInputs = mutableListOf<AudioTrackInputConfig>()
    val trackInputs: List<AudioTrackInputConfig> get() = _trackInputs.toList()

    /**
     * 添加一条音频轨道
     */
    fun addTrack(config: AudioTrackInputConfig) {
        // 检查 trackId 是否重复
        require(_trackInputs.none { it.trackId == config.trackId }) {
            "trackId ${config.trackId} already exists"
        }
        _trackInputs.add(config)
    }

    /**
     * 移除指定 trackId 的音频轨道
     *
     * @return 是否成功移除
     */
    fun removeTrack(trackId: Int): Boolean {
        return _trackInputs.removeAll { it.trackId == trackId }
    }

    /**
     * 获取指定 trackId 的轨道配置
     */
    fun getTrack(trackId: Int): AudioTrackInputConfig? {
        return _trackInputs.find { it.trackId == trackId }
    }

    /**
     * 是否有附加音频轨道
     */
    fun hasExtraTracks(): Boolean = _trackInputs.isNotEmpty()

    /**
     * 清空所有附加音频轨道
     */
    fun clearTracks() {
        _trackInputs.clear()
    }
}
