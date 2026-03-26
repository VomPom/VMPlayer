package com.vompom.media.render

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import com.vompom.media.player.PlayerView
import com.vompom.media.utils.VLog
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGL10

/**
 *
 * Created by @juliswang on 2025/11/20 21:03
 *
 * @Description 实现逻辑参考 Android 官方 GLSurfaceView 以及 GPUImage 源码
 *
 *  EGL/OpenGL ES 初始化和渲染流程
 *  阶段一：
 *      EGL 初始化 (建立连接)
 *
 * 1. 获取显示连接,eglGetDisplay(),获取与本地显示系统（通常是 GPU）的连接句柄。传入 EGL_DEFAULT_DISPLAY。
 * 2. 初始化 EGL,eglInitialize(),初始化 EGL 库。成功后，EGL 版本信息会被返回。
 * 3. 配置选择,eglChooseConfig(),"定义渲染环境特性，如颜色深度（RGB888, RGBA8888）、深度/模板缓冲区大小、是否支持 OpenGL ES 版本等。"
 * 4. 创建渲染表面,eglCreateWindowSurface(),使用上一步选择的配置，以及本地窗口（在 Android 上通常是 SurfaceView 或 SurfaceTexture 提供的 ANativeWindow*），创建 EGL 渲染表面 (EGLSurface)。最终绘制结果输出的地方。
 * 5. 创建渲染上下文,eglCreateContext(),创建 OpenGL ES 渲染上下文 (EGLContext)。
 *
 *  阶段二：
 *      OpenGL ES 渲染 (执行绘制)
 *
 * 6. 关联上下文,eglMakeCurrent(),将第 4 步创建的 EGLSurface（绘图目标）和第 5 步创建的 EGLContext（状态机）关联到当前线程。只有在调用此函数后，才能开始调用 OpenGL ES 函数。
 * 7. 渲染命令,gl...(),调用标准的 OpenGL ES 绘图命令：设置视口、编译/链接 Shaders、创建 VBO/VAO、设置uniforms/attributes、调用 glDraw... 等。
 * 8. 交换缓冲区,eglSwapBuffers(),将当前渲染帧缓冲区中的内容提交到显示表面 (EGLSurface)，使其在屏幕上可见。这是帧的结束点。
 *
 * 阶段三：
 *      清理和销毁
 *
 * 9. 释放当前上下文,"eglMakeCurrent(EGL_NO_SURFACE, ...)",将上下文从当前线程分离。
 * 10. 销毁资源,eglDestroySurface(),销毁渲染表面。
 * 11. 销毁上下文,eglDestroyContext(),销毁渲染上下文。
 * 12. 终止 EGL,eglTerminate(),释放 EGL 显示连接和所有相关资源。
 */
class EglHelper(val encodeSurface: Surface? = null, val playerView: WeakReference<PlayerView>?, val renderSize: Size) {
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var eglConfig: EGLConfig? = null


    /**
     * Initialize EGL for a given configuration spec.
     */
    fun setup() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            glLogE("eglGetDisplay failed")
            return
        }
        val version = IntArray(2)
        // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE
        )

        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = null
            glLogE("eglInitialize failed")
            return
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                eglDisplay, attribList, 0, configs, 0, configs.size,
                numConfigs, 0
            )
        ) {
            throw RuntimeException("unable to find RGB888+recordable ES2 EGL config")
        }
        eglConfig = configs[0]
        val eglContextAttribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION,
            2,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            eglContextAttribList, 0
        )
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            glLogE("eglCreateContext failed")
            return
        }
    }

    fun createEGLSurface() {
        // todo: optimize use surface instead of surfaceTexture
        val surfaceTexture = playerView?.get()?.surfaceTexture
        if (encodeSurface != null || surfaceTexture != null) {
            // Create a window surface, and attach it to the Surface we received.
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            if (encodeSurface != null) {
                eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, encodeSurface,
                    surfaceAttribs, 0
                )
            }
            if (surfaceTexture != null) {
                eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, surfaceTexture,
                    surfaceAttribs, 0
                )
            }
        } else {
            val surfaceAttribList = intArrayOf(
                EGL14.EGL_WIDTH, renderSize.width,
                EGL14.EGL_HEIGHT, renderSize.height,
                EGL14.EGL_NONE
            )
            eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribList, 0)
        }

        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            glLogE("eglCreatePbufferSurface failed")
            throw RuntimeException("Failed to create EGL surface")
        }

        /*
         * Before we can issue GL commands, we need to make sure
         * the context is current and bound to a surface.
         */
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            /*
             * Could not make the context current, probably because the underlying
             * TextureView surface has been destroyed.
             */
            glLogE("eglMakeCurrent failed")
            throw RuntimeException("Failed to make EGL context current")
        }
    }


    /**
     * Display the current render surface.
     *
     * @return the EGL error code from eglSwapBuffers.
     */
    fun swap(): Int {
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            return EGL14.eglGetError()
        }
        return EGL10.EGL_SUCCESS
    }

    fun destroy() {
        if (eglSurface != null && eglSurface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(
                eglDisplay,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
            eglContext = EGL14.EGL_NO_CONTEXT
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    private fun glLogE(msg: String?) = VLog.e("EGLHelper: " + msg + ", err=" + GLES20.glGetError())

}


















