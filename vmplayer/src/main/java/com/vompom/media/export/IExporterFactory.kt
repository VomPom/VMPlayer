package com.vompom.media.export

import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/12/20 19:00
 *
 * @Description 导出器工厂：核心模块不感知具体实现，由特效模块实现并在构造 [com.vompom.media.VMPlayer] 时注入。
 *              这样核心模块不需要依赖 OpenGL / 特效相关的 PlayerRender。
 */
interface IExporterFactory {
    /**
     * 构造一个可用的导出器。
     *
     * @param segments          当前播放列表对应的轨道片段
     * @param audioMixConfig    当前的音频混音配置，可为空表示使用原始音频
     */
    fun create(segments: List<TrackSegment>, audioMixConfig: AudioMixConfig?): IExporter
}
