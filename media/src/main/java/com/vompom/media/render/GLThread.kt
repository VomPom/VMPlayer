package com.vompom.media.render

import android.graphics.SurfaceTexture
import android.util.Size
import com.vompom.media.utils.VLog
import java.lang.ref.WeakReference

/**
 *
 * Created by @juliswang on 2025/12/12 11:20
 *
 * @Description
 */
class GLThread : Thread {
    val renderer: PlayerRender
    val palerView: WeakReference<PlayerView>?
    val renderSize: Size

    constructor(
        renderer: PlayerRender,
        palerView: WeakReference<PlayerView>? = null,
        renderSize: Size
    ) : super(buildString {
        append("GLT-Render-Thread-")
        append(if (palerView != null) "Preview" else "Export")
    }) {
        this.renderer = renderer
        this.palerView = palerView
        this.renderSize = renderSize
        this.eventQueue = ArrayList<Runnable>()
        this.glThreadObject = Object()
    }

    private lateinit var eglHelper: EglHelper
    private val eventQueue: ArrayList<Runnable>
    private var currentEvent: Runnable? = null

    private var hasViewSurface = false      // 这里标记的是 用于上屏幕的 Surface 是否创建（也就是 TextureView 的Surface）
    private var hasEGLSurface = false       // 标记的是 OpenGL 的 Surface
    private var haveEglContext = false

    private val glThreadObject: Object

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

                // 导出模式只需要 EGLSurface 是否创建好
                if ((hasViewSurface || isExportMode()) && !hasEGLSurface) {
                    try {
                        eglHelper.createEGLSurface()
                        renderer.onEGLSurfaceCreated()
                        hasEGLSurface = true
                        VLog.d("EGL surface created successfully")
                    } catch (e: Exception) {
                        VLog.e("Failed to create EGL surface: ${e.message}")
                        // 在导出模式下，如果EGLSurface创建失败，需要重新初始化EGL
                        if (isExportMode()) {
                            VLog.e("Export mode: EGL surface creation failed, will retry EGL setup")
                            eglHelper.destroy()
                            haveEglContext = false
                            hasEGLSurface = false

                            // 等待一小段时间后重试
                            try {
                                glThreadObject.wait(100)
                            } catch (ie: InterruptedException) {
                                ie.printStackTrace()
                            }
                        }
                    }
                }

                if ((hasViewSurface || isExportMode()) && haveEglContext && hasEGLSurface) {
                    try {
                        renderer.onDrawFrame()
                        eglHelper.swap()
                    } catch (e: Exception) {
                        VLog.e("Error during rendering: ${e.message}")
                    }
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
        hasViewSurface = true
    }

    fun surfaceDestroyed() {
        eglHelper.destroy()
        renderer.release()
    }

    fun surfaceChanged(texture: SurfaceTexture?, w: Int, h: Int) {
        renderer.onSurfaceChanged(w, h)
    }

    private fun isExportMode(): Boolean {
        return palerView == null
    }
}