package com.vompom.media.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Size
import android.view.Surface

/**
 * 视频渲染视图
 *
 * 集成了VideoRenderProcessor，提供视频缩放适配和特效处理功能
 */
class VideoRenderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private var playerRender: PlayerRenderer? = null
    private var renderSize = Size(0, 0)
    private var isRendererSet = false
    private var surfaceReadyCallback: ((Surface) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        initRenderProcessor()
    }

    private fun initRenderProcessor() {
        playerRender = PlayerRenderer(renderSize).apply {
            setSurfaceReadyCallback { surface ->
                surfaceReadyCallback?.invoke(surface)
            }
            setRenderRequestCallback {
                // 在GL线程中请求渲染
                requestRender()
            }
            VideoRenderView@ setRenderer(this)
        }

        renderMode = RENDERMODE_CONTINUOUSLY  // 改为连续渲染模式，确保视频播放流畅
        isRendererSet = true
    }

    /**
     * 设置目标渲染尺寸
     */
    fun setTargetRenderSize(size: Size) {
        if (renderSize != size) {
            renderSize = size
            // 通知渲染处理器更新参数
            queueEvent {
                playerRender?.updateRenderSize(renderSize)
                requestRender()
            }
        }
    }

    /**
     * 更新视频尺寸
     */
    fun updateVideoSize(videoSize: Size) {
        queueEvent {
            playerRender?.updateVideoSize(videoSize)
            requestRender()
        }
    }

    /**
     * 设置视频特效处理器
     */
    fun setEffectProcessor(processor: VideoEffectProcessor) {
        queueEvent {
            playerRender?.setEffectProcessor(processor)
            requestRender()
        }
    }

    /**
     * 设置Surface准备完成的回调
     */
    fun setSurfaceReadyCallback(callback: (Surface) -> Unit) {
        surfaceReadyCallback = callback
        // 如果Surface已经准备好，立即回调
        playerRender?.getInputSurface()?.let { surface ->
            callback(surface)
        }
    }

    /**
     * 释放资源
     */
    fun releaseResources() {
        queueEvent {
            playerRender?.release()
        }
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        releaseResources()
    }
}