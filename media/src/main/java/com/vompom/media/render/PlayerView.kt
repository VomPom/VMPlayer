package com.vompom.media.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Size
import android.view.TextureView
import java.lang.ref.WeakReference

/**
 *
 * Created by @juliswang on 2025/11/20 20:27
 *
 * @Description 视频源 → SurfaceTexture → OpenGL纹理 → 滤镜处理 → 渲染到TextureView
 */

class PlayerView : TextureView, TextureView.SurfaceTextureListener, IPlayerView {
    private var glThread: GLThread? = null
    private var renderSize = Size(0, 0)

    constructor(context: Context) : super(context) {
        init()
    }

    private fun init() {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(
        surfaceTexture: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        glThread?.surfaceCreated()
        glThread?.surfaceChanged(surfaceTexture, width, height)
    }

    override fun onSurfaceTextureSizeChanged(
        surface: SurfaceTexture,
        width: Int,
        height: Int
    ) {
        glThread?.surfaceChanged(surface, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        glThread?.surfaceDestroyed()
        return false
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

    }

    override fun setRenderSize(size: Size) {
        this.renderSize = size
    }

    override fun updateVideoSize(size: Size) {

    }

    override fun release() {

    }

    fun setRenderer(renderer: PlayerRender) {
        glThread = GLThread(
            renderer, WeakReference(this),
            renderSize
        )
        glThread?.start()
    }
}