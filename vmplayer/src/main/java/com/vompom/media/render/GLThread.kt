package com.vompom.media.render

import android.graphics.SurfaceTexture
import android.util.Size
import android.view.Surface
import com.vompom.media.IQueueEvent
import com.vompom.media.player.PlayerView
import com.vompom.media.utils.VLog
import java.lang.ref.WeakReference

/**
 *
 * Created by @juliswang on 2025/12/12 11:20
 *
 * @Description OpenGL 渲染线程，负责在单独线程中驱动 EGL 环境与渲染循环。
 * 生命周期标记：
 * - `hasViewSurface`：标记用于上屏的 View Surface（如 TextureView 的 Surface）是否已创建
 * - `hasEGLSurface`：标记 EGL 的渲染 Surface 是否已成功创建
 * - `haveEglContext`：标记 EGL 上下文是否已初始化
 */
class GLThread : Thread, IQueueEvent {
    val renderer: PlayerRender
    val surface: Surface?
    val renderSize: Size
    val playerView: WeakReference<PlayerView>?

    constructor(
        renderer: PlayerRender,
        surface: Surface? = null,
        playerView: WeakReference<PlayerView>? = null,
        renderSize: Size
    ) : super(buildString {
        append(if (playerView != null) "Preview" else "Export")
    }) {
        this.renderer = renderer
        this.surface = surface
        this.playerView = playerView
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
        eglHelper = EglHelper(surface, playerView, renderSize)

        while (true) {
            if (!eventQueue.isEmpty()) {
                currentEvent = eventQueue.removeAt(0)
                currentEvent?.run()
            }

            synchronized(glThreadObject) {
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

    override fun queueEvent(r: Runnable) {
        eventQueue.add(r)
    }

    /**
     * 当用于上屏的 View Surface（如 TextureView 的 SurfaceTexture）创建完成时调用 onSurfaceTextureAvailable
     * 标记可以创建对应的 EGLSurface 进行预览渲染。
     */
    fun surfaceCreated() {
        hasEGLSurface = false
        hasViewSurface = true
    }

    /**
     * 当 View 的 Surface 被销毁时调用，负责释放 EGL 资源并通知渲染器释放内部资源。
     */
    fun surfaceDestroyed() {
        eglHelper.destroy()
        renderer.release()
    }

    /**
     * 当 Surface 尺寸发生变化时（如 View 大小改变），通知渲染器调整视口等相关状态。
     */
    fun surfaceChanged(texture: SurfaceTexture?, w: Int, h: Int) {
        renderer.onSurfaceChanged(w, h)
    }

    /**
     * 判断当前是否处于导出模式：
     * - `playerView == null` 表示没有预览视图，当前为离屏导出（编码）场景。
     */
    private fun isExportMode(): Boolean = playerView == null
}