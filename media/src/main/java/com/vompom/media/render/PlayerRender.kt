package com.vompom.media.render

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES
import android.opengl.GLES20
import android.util.Size
import android.view.Surface
import com.vompom.media.render.utils.GLUtils
import com.vompom.media.render.utils.GLUtils.checkGLError
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil

/**
 *
 * Created by @juliswang on 2025/11/20 21:28
 *
 * @Description
 */

class PlayerRender() {
    companion object {

        private const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTextureCoord;
        varying vec2 vTextureCoord;
        
        uniform float contentRatio;   // contentRatio: 渲染内容的 宽度 / 高度
        uniform float viewRatio;      // viewRatio: 画布 (TextureView) 的 宽度 / 高度
        
        void main() {
            // 初始化缩放和平移向量
            vec2 scale = vec2(1.0, 1.0);
            // 核心逻辑: 实现 "保持原始比例并居中" (Contain / Fit 模式)
            if (contentRatio > viewRatio) {
                // 场景 1: 内容更宽 (内容更扁，例如 16:9 放在 4:3 画布上)
                scale.y = viewRatio / contentRatio; 
                scale.x = 1.0;
            } else {
                // 场景 2: 内容更高 (内容更瘦，例如 4:3 放在 16:9 画布上)
                scale.x = contentRatio / viewRatio;
                scale.y = 1.0;
            }
            // aPosition 是原始的 [-1, -1] 到 [1, 1] 坐标
            // 因为缩放是居中进行的，所以不需要额外的平移 (offset) 即可居中
            gl_Position = vec4(aPosition.xy * scale, 0.0, 1.0);
    
            // 传递纹理坐标给片元着色器
            vTextureCoord = aTextureCoord;
        }         
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }

    // 解码器、渲染链将会渲染的目标（并非上屏的Surface）
    private var renderSurfaceTexture: SurfaceTexture? = null
    private var surfaceReadyCallback: ((Surface) -> Unit)? = null

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    private val frameSyncObject = Object()
    private var frameAvailable = false
    private var renderSize: Size = Size(0, 0)
    private var surfaceSize: Size = Size(0, 0)

    private var glProgram = 0
    private var vertexShader = 0
    private var fragmentShader = 0
    private var oesTextureId = 0

    fun setSurfaceReadyCallback(callback: (Surface) -> Unit) {
        surfaceReadyCallback = callback
    }

    fun onEGLSurfaceCreated() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        setupShaders()
        createOutputSurface()
        setupBuffers()
    }

    private fun setupBuffers() {
        // 顶点坐标 (x, y, z)
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0f,
            1.0f, -1.0f, 0f,
            -1.0f, 1.0f, 0f,
            1.0f, 1.0f, 0f
        )

        val textureCoords = floatArrayOf(
            0.0f, 1.0f,  // 左上
            1.0f, 1.0f,  // 右上
            0.0f, 0.0f,  // 左下
            1.0f, 0.0f   // 右下
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureBuffer.position(0)
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


    private fun setupShaders() {
        vertexShader = GLUtils.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        fragmentShader = GLUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw RuntimeException("Could not link program: ${GLES20.glGetProgramInfoLog(glProgram)}")
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        surfaceSize = Size(width, height)
    }

    fun initRenderSize(size: Size) {
        this.renderSize = size
    }

    fun onDrawFrame() {
        // === 阶段0: 纹理更新  ===
        awaitNewImage()
        onDraw()
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

    private fun onDraw() {
        // === 阶段1: 状态设置 ===
        GLES20.glClearColor(0.0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // === 阶段2: 渲染设置 ===
        GLES20.glUseProgram(glProgram)
        checkGLError("glUseProgram")

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, oesTextureId)

        // 设置采样器
        val samplerHandle = GLES20.glGetUniformLocation(glProgram, "sTexture")
        if (samplerHandle >= 0) {
            GLES20.glUniform1i(samplerHandle, 0)
        }

        // 设置顶点属性
        val positionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
        val textureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
        val uContentRatioHandle = GLES20.glGetUniformLocation(glProgram, "contentRatio")
        val uViewRatioHandle = GLES20.glGetUniformLocation(glProgram, "viewRatio")

        GLES20.glUniform1f(uViewRatioHandle, surfaceSize.width.toFloat() / surfaceSize.height.toFloat())
        GLES20.glUniform1f(uContentRatioHandle, renderSize.width.toFloat() / renderSize.height.toFloat())

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        // === 阶段3: 绘制调用 ===
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 检查OpenGL错误
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            VLog.e("OpenGL error in renderFrame: $error")
        }
        // === 阶段4: 缓冲区交换 ===
        // eglSwapBuffers() 在渲染线程中调用
    }

    fun release() {
        renderSurfaceTexture?.release()
    }
}