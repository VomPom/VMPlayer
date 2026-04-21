package com.vompom.media

import android.util.Size
import com.vompom.media.export.IExporter
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.ClipAsset

/**
 *
 * Created by @juliswang on 2025/09/28 20:08
 *
 * @Description 定义播放器提供的接口
 */

interface IPlayer {
    fun setPlayList(videoList: List<ClipAsset>)
    fun play()
    fun pause()
    fun seekTo(positionUs: Long)
    fun stop()
    fun release()
    fun duration(): Long
    fun setLoop(loop: Boolean)
    fun setRenderSize(size: Size)
    fun setPlayerListener(listener: PlayerListener)
    fun createExporter(): IExporter

    /**
     * 设置多轨道音频混音配置
     * @param config 混音配置，包含所有附加音频轨道的参数
     */
    fun setAudioMix(config: AudioMixConfig)

    /**
     * 移除音频混音配置，恢复只播放原始音频
     */
    fun removeAudioMix()

    interface PlayerListener {
        fun onPositionChanged(currentDurationUs: Long, playerDurationUs: Long)
    }
}