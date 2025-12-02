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

    class GLThread(
        val renderer: PlayerRender,
        val palerView: WeakReference<PlayerView>,
        val renderSize: Size
    ) : Thread("GLT-Render-Thread") {
        private lateinit var eglHelper: EglHelper
        private val eventQueue = ArrayList<Runnable>()
        private var currentEvent: Runnable? = null

        private var hasSurface = false          // 这里标记的是 用于上屏幕的 Surface 是否创建（也就是 TextureView 的Surface）
        private var hasEGLSurface = false       // 标记 OpenGL 的 Surface
        private var haveEglContext = false

        private val glThreadObject = Object()

        override fun run() {
            eglHelper = EglHelper(palerView, renderSize)
            while (true) {
                synchronized(glThreadObject) {
                    if (!eventQueue.isEmpty()) {
                        currentEvent = eventQueue.removeAt(0)
                        currentEvent?.run()
                    }

                    if (!haveEglContext) {
                        eglHelper.setup()
                        haveEglContext = true
                    }

                    if (hasSurface && !hasEGLSurface) {
                        eglHelper.createEGLSurface()
                        renderer.onEGLSurfaceCreated()
                        hasEGLSurface = true
                    }

                    if (hasSurface && haveEglContext) {
                        renderer.onDrawFrame()
                        eglHelper.swap()
                    }
                }
            }
        }

        fun queueEvent(r: Runnable) {
            synchronized(glThreadObject) {
                eventQueue.add(r)
                glThreadObject.notifyAll()
            }
        }

        fun surfaceCreated() {
            hasEGLSurface = false
            hasSurface = true
        }

        fun surfaceDestroyed() {
            eglHelper.destroy()
            renderer.release()
        }

        fun surfaceChanged(texture: SurfaceTexture?, w: Int, h: Int) {
            renderer.onSurfaceChanged(w, h)
        }
    }
}