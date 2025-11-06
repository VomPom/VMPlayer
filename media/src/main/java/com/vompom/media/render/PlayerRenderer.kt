package com.vompom.media.render

import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Size
import android.view.Surface
import com.vompom.media.utils.VLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 视频渲染处理器
 *
 * 功能：
 * 1. 处理不同尺寸视频的缩放适配（FIT/FILL模式）
 * 2. 提供视频特效处理接口
 * 3. 管理OpenGL渲染流程
 */
class PlayerRenderer(private var renderSize: Size) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private var glProgram = 0
    private var vertexShader = 0
    private var fragmentShader = 0
    private var textureId = 0
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val textureMatrix = FloatArray(16)

    // 顶点和纹理坐标
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    // Surface和纹理
    var surfaceTexture: SurfaceTexture? = null
        private set
    var surface: Surface? = null
        private set

    // 当前视频尺寸
    private var currentVideoSize = Size(0, 0)
    private var updateSurface = false
    private var isTestMode = false  // 测试模式，用于诊断
    private var hasVideoData = false  // 是否有视频数据
    private var viewportSize = Size(0, 0)  // 实际视口尺寸
    private var useSurfaceTextureMatrix = true  // 是否使用SurfaceTexture变换矩阵

    private var effectProcessor: VideoEffectProcessor? = null
    private var surfaceReadyCallback: ((Surface) -> Unit)? = null
    private var renderRequestCallback: (() -> Unit)? = null

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        // 顶点着色器
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTextureMatrix;
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTextureMatrix * vec4(aTextureCoord, 0.0, 1.0)).xy;
            }
        """

        // 片段着色器
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

    init {
        Matrix.setIdentityM(textureMatrix, 0)
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        setupShaders()
        setupTexture()
        setupBuffers()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        val (position, size) = initGLViewportFit(Size(width, height), renderSize)
        GLES20.glViewport(position.width, position.height, size.width, size.height)

        // 保存视口尺寸
        viewportSize = Size(width, height)

        // 使用简单的正交投影矩阵，不考虑屏幕宽高比
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, 1f, 10f)

        // 使用简单的视图矩阵
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)

        // 初始化模型矩阵
        Matrix.setIdentityM(modelMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        // 根据模式设置背景色
        if (isTestMode) {
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)  // 测试模式：绿色背景
        } else {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)  // 正常模式：黑色背景
        }
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        if (surfaceTexture == null) {
            VLog.e("surfaceTexture is null in onDrawFrame")
            return
        }

        var frameUpdated = false

        synchronized(this) {
            if (updateSurface) {
                try {
                    surfaceTexture?.updateTexImage()
                    surfaceTexture?.getTransformMatrix(textureMatrix)
                    frameUpdated = true
                    hasVideoData = true  // 标记已有视频数据
                } catch (e: Exception) {
                    VLog.e("Error updating TexImage:${e.message}")
                }
                updateSurface = false
            }
        }

        // 应用视频特效（如果有）
        effectProcessor?.processFrame(textureId, currentVideoSize)

        // 只有在有视频数据且不是测试模式时才渲染视频帧
        if (!isTestMode && hasVideoData) {
            renderFrame()
        }
    }

    @Synchronized
    override fun onFrameAvailable(surface: SurfaceTexture) {
        updateSurface = true
        // 请求重新渲染
        renderRequestCallback?.invoke()
    }

    /**
     * 更新视频尺寸，重新计算变换矩阵
     */
    fun updateVideoSize(videoSize: Size) {
        currentVideoSize = videoSize
    }

    /**
     * 更新目标渲染尺寸
     */
    fun updateRenderSize(newRenderSize: Size) {
        if (renderSize != newRenderSize) {
            renderSize = newRenderSize
        }
    }

    /**
     * 计算画面保持原始的比例，周围留黑边的数据
     *
     * @param layerSize     画布的尺寸
     * @param renderSize    希望渲染的画面尺寸
     * @return
     */
    fun initGLViewportFit(layerSize: Size, renderSize: Size): Pair<Size, Size> {
        val rHeight: Int = renderSize.height
        val rWidth: Int = renderSize.width

        val vHeight: Int = layerSize.height
        val vWidth: Int = layerSize.width

        var x = 0
        var y = 0

        val resultWidth: Int
        val resultHeight: Int

        // view的宽高比更大，view更扁、更宽
        if (vWidth / vHeight > rWidth / rHeight) {
            // 使用view的高度，缩放renderHeight
            resultWidth = rWidth * vHeight / rHeight
            resultHeight = vHeight
            // x轴挪动保持居中，y不变
            x = (vWidth - resultWidth) / 2
        } else { //view 更细长
            // 使用view的宽度，缩放renderWidth
            resultWidth = vWidth
            resultHeight = rHeight * vWidth / rWidth
            // y轴挪动保持居中，x不变
            y = (vHeight - resultHeight) / 2
        }

        return Pair(Size(x, y), Size(resultWidth, resultHeight))
    }


    /**
     * 设置视频特效处理器
     */
    fun setEffectProcessor(processor: VideoEffectProcessor) {
        effectProcessor = processor
    }

    /**
     * 设置Surface准备完成的回调
     */
    fun setSurfaceReadyCallback(callback: (Surface) -> Unit) {
        surfaceReadyCallback = callback
    }

    /**
     * 设置渲染请求回调
     */
    fun setRenderRequestCallback(callback: () -> Unit) {
        renderRequestCallback = callback
    }

    /**
     * 获取输入Surface，供MediaCodec使用
     */
    fun getInputSurface(): Surface? = surface

    private fun setupShaders() {
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

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

    private fun setupTexture() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        VLog.d("Generated texture id: $textureId")

        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture?.setOnFrameAvailableListener(this)
        surface = Surface(surfaceTexture)
        VLog.d("SurfaceTexture and inputSurface created")

        // Surface创建完成后通知回调
        surface?.let { surface ->
            surfaceReadyCallback?.invoke(surface)
        }
    }

    private fun setupBuffers() {
        // 顶点坐标 (x, y, z)
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0f,
            1.0f, -1.0f, 0f,
            -1.0f, 1.0f, 0f,
            1.0f, 1.0f, 0f
        )

        // 修复纹理坐标，解决画面翻转问题
        val textureCoords = floatArrayOf(
            0f, 0f,  // 左下角
            1f, 0f,  // 右下角
            0f, 1f,  // 左上角
            1f, 1f   // 右上角
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


    private fun renderFrame() {
        GLES20.glUseProgram(glProgram)

        // 检查程序是否有效
        val programValid = IntArray(1)
        GLES20.glValidateProgram(glProgram)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_VALIDATE_STATUS, programValid, 0)
        if (programValid[0] == 0) {
            VLog.e("Program validation failed: ${GLES20.glGetProgramInfoLog(glProgram)}")
            return
        }

        // 修正MVP矩阵计算顺序：Projection * View * Model
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)

        // 减少日志频率 - 只在第一次或矩阵改变时打印
        if (modelMatrix[0] != 1.0f || modelMatrix[5] != 1.0f) {
            VLog.d("renderFrame - Model matrix scale: [${modelMatrix[0]}, ${modelMatrix[5]}]")
        }

        val mvpMatrixHandle = GLES20.glGetUniformLocation(glProgram, "uMVPMatrix")
        val textureMatrixHandle = GLES20.glGetUniformLocation(glProgram, "uTextureMatrix")
        val positionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
        val textureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")

        // 检查句柄是否有效
        if (mvpMatrixHandle == -1 || textureMatrixHandle == -1 || positionHandle == -1 || textureHandle == -1) {
            VLog.e(
                "Invalid handles: mvp=$mvpMatrixHandle, texture=$textureMatrixHandle, pos=$positionHandle, tex=$textureHandle"
            )
            return
        }

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 测试：尝试不使用 SurfaceTexture 的变换矩阵
        if (useSurfaceTextureMatrix) {
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)
        } else {
            // 使用单位矩阵替代
            val identityMatrix = FloatArray(16)
            Matrix.setIdentityM(identityMatrix, 0)
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, identityMatrix, 0)
        }

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, textureId)

        // 设置采样器
        val samplerHandle = GLES20.glGetUniformLocation(glProgram, "sTexture")
        if (samplerHandle >= 0) {
            GLES20.glUniform1i(samplerHandle, 0)
        }

        // 设置顶点属性
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 检查OpenGL错误
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            VLog.e("OpenGL error in renderFrame: $error")
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(textureHandle)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader: $error")
        }
        return shader
    }

    fun release() {
        surfaceTexture?.setOnFrameAvailableListener(null)
        surface?.release()
        surfaceTexture?.release()

        // 重置状态
        hasVideoData = false
        updateSurface = false

        if (glProgram != 0) {
            GLES20.glDeleteProgram(glProgram)
        }
        if (vertexShader != 0) {
            GLES20.glDeleteShader(vertexShader)
        }
        if (fragmentShader != 0) {
            GLES20.glDeleteShader(fragmentShader)
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }
}

/**
 * 视频特效处理器接口
 */
interface VideoEffectProcessor {
    /**
     * 处理视频帧
     * @param textureId 纹理ID
     * @param videoSize 视频尺寸
     */
    fun processFrame(textureId: Int, videoSize: Size)
}