package com.vompom.media

import android.util.Size
import android.view.Surface

/**
 *
 * Created by @juliswang on 2025/12/20 19:10
 *
 * @Description 渲染侧 Surface 提供者的抽象：
 *              - 核心播放模块不感知具体的 OpenGL / 特效逻辑，
 *              - 由 `vmplayer-effect` 模块提供具体实现（持有 PlayerView + PlayerRender + GLThread）。
 */
interface IRenderSurfaceProvider {

    /**
     * 设置渲染尺寸（即目标视频分辨率），由播放器对接时调用。
     */
    fun setRenderSize(size: Size)

    /**
     * 设置 Surface 就绪回调：当特效侧的 OES SurfaceTexture 创建完成后，
     * 会通过该回调把可供解码器写入的 Surface 抛给播放器。
     */
    fun setOnSurfaceReady(callback: (Surface) -> Unit)

    /**
     * 当视频原始尺寸（宽高）发生变化时通知到渲染 View，
     * 通常由 `VideoDecoderTrack` 在 MediaExtractor 解析出视频尺寸时调用。
     */
    fun updateVideoSize(size: Size)

    /**
     * 释放渲染相关资源。
     */
    fun release()
}
