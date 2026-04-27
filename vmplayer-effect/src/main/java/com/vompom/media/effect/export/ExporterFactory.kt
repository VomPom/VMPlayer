package com.vompom.media.effect.export

import com.vompom.media.effect.IRenderSession
import com.vompom.media.export.IExporter
import com.vompom.media.export.IExporterFactory
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/12/20 19:00
 *
 * @Description 基于渲染侧 [IRenderSession] 的导出器工厂，负责把当前渲染链中的
 *              特效 / 贴纸等状态打包到 [RenderModel]，再交给 [Exporter] 执行离屏合成。
 */
class ExporterFactory(
    private val renderSession: IRenderSession
) : IExporterFactory {

    override fun create(segments: List<TrackSegment>, audioMixConfig: AudioMixConfig?): IExporter {
        val renderModel = renderSession.getRenderModel()
        return Exporter(segments, renderModel, audioMixConfig)
    }
}
