package com.vompom.media.render

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import com.vompom.media.model.TextureInfo
import com.vompom.media.render.effect.EffectGroup
import com.vompom.media.utils.GLUtils
import com.vompom.media.utils.VLog
import kotlin.math.ceil

/**
 *
 * Created by @juliswang on 2025/11/20 21:28
 *
 * @Description 渲染特效组
 */
class PlayerRender() : IRendererEffect {

    // 解码器、渲染链将会渲染的目标（并非上屏的Surface）
    private var renderSurfaceTexture: SurfaceTexture? = null
    private var surfaceReadyCallback: ((Surface) -> Unit)? = null

    private val frameSyncObject = Object()
    private var frameAvailable = false

    private var renderSize: Size = Size(0, 0)       // 目标渲染的尺寸
    private var surfaceSize: Size = Size(0, 0)      // 画布(View)的尺寸
    private var oesTextureId = 0
    private var effectGroup: EffectGroup? = null


    fun setSurfaceReadyCallback(callback: (Surface) -> Unit) {
        surfaceReadyCallback = callback
    }

    override fun setEffectGroup(effects: EffectGroup?) {
        this.effectGroup = effects
    }

    fun onEGLSurfaceCreated() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        createOutputSurface()
    }

    /**
     * 这里创建的SurfaceTexture是用来接收视频解码后的图像数据， 然后通过OpenGL ES将图像数据渲染到屏幕上。
     *
     */
    private fun createOutputSurface() {
        oesTextureId = GLUtils.createTexture(GL_TEXTURE_EXTERNAL_OES)

        SurfaceTexture(oesTextureId).apply {
            renderSurfaceTexture = this
            surfaceReadyCallback?.invoke(Surface(this))
            setOnFrameAvailableListener(object : OnFrameAvailableListener {
                override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
                    synchronized(frameSyncObject) {
                        frameAvailable = true
                        frameSyncObject.notifyAll()
                    }
                }
            })
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceSize = Size(width, height)
        effectGroup?.updateRenderViewSize(surfaceSize)
    }

    fun initRenderSize(size: Size) {
        this.renderSize = size
        effectGroup?.updateRenderViewSize(size)
    }

    fun onDrawFrame() {
        awaitNewImage()
        handleEffect()
    }

    private fun handleEffect() {
        val inputTexture = TextureInfo(
            oesTextureId,
            GL_TEXTURE_EXTERNAL_OES,
            renderSize.width,
            renderSize.height
        )

        effectGroup?.applyNewTexture(inputTexture)
    }

    /**
     * 不需要每次都执行 updateTexImage，需要在 onFrameAvailable 通知之后再执行 updateTexImage 进行 openGL 渲染
     *
     */
    private fun awaitNewImage(timeoutMs: Long = 3000, tryPerTimeMs: Int = 50) {
        var needRetryTimes = ceil((timeoutMs * 1f / tryPerTimeMs).toDouble()).toInt()
        synchronized(frameSyncObject) {
            while (!frameAvailable && needRetryTimes > 0) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    needRetryTimes--
                    frameSyncObject.wait(tryPerTimeMs.toLong())
                } catch (ie: InterruptedException) {
                    ie.printStackTrace()
                }
            }
            frameAvailable = false
            if (needRetryTimes == 0) {
                return
            }
        }
        try {
            renderSurfaceTexture?.updateTexImage()
        } catch (e: Exception) {
            VLog.e("Error updating TexImage:${e.message}")
        }
    }

    fun release() {
        renderSurfaceTexture?.release()
    }
}

//todo:: 后面干掉这个接口，不太优雅
interface IRendererEffect {
    fun setEffectGroup(effects: EffectGroup?)
}