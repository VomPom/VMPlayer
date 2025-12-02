package com.vompom.media.render.effect

import android.opengl.GLES20
import android.util.Size
import com.vompom.media.model.TextureInfo
import com.vompom.media.model.TimeRange
import com.vompom.media.utils.GLUtils
import com.vompom.media.utils.GLUtils.checkGLError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 *
 * Created by @juliswang on 2025/11/27 10:53
 *
 * @Description
 */

abstract class BaseEffect : IFilter {
    companion object {
        private const val VERTEX_SHADER_CODE = """
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
        private const val F_BUFFER_CNT = 1
    }

    /**
     * 屏幕绘制区域宽高
     */
    protected var renderViewSize: Size = Size(0, 0)

    protected var timeRange: TimeRange? = null

    /**
     * 纹理类型，yuv或者rgb/rgba
     */
    protected var textureType: Int = 0
    protected var glProgram: Int = 0

    private var aPositionHandle = -1
    private var aTextureHandle = -1
    private var uContentRatioHandle = -1
    private var uViewRatioHandle = -1

    private var vertexBuffer: FloatBuffer = FloatBuffer.allocate(0)
    private var textureBuffer: FloatBuffer = FloatBuffer.allocate(0)

    protected var defaultViewport: IntArray = IntArray(4)

    private val frameBuffers = IntArray(F_BUFFER_CNT)
    protected var frameBufferTextures: IntArray = IntArray(F_BUFFER_CNT)

    fun updateRenderViewSize(size: Size) {
        renderViewSize = size
    }

    override fun applyNewTexture(inputTextureInfo: TextureInfo): TextureInfo {
        if (inputTextureInfo.textureType != textureType || glProgram == 0) {
            initShader(inputTextureInfo)
            initBuffers()
        }

        bindFramebuffer()

        useProgram()

        beforeDraw(inputTextureInfo)

        onDraw(inputTextureInfo)

        afterDraw(inputTextureInfo)

        finishDraw(inputTextureInfo)

        return getOutputTextureInfo(inputTextureInfo)
    }

    open fun initShader(inputTexture: TextureInfo) {
        val fragmentShaderCode: String = getFragmentShaderCode(inputTexture)
        val vertexShader = GLUtils.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = GLUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        glProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(glProgram, vertexShader)
        GLES20.glAttachShader(glProgram, fragmentShader)
        GLES20.glLinkProgram(glProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw RuntimeException("Could not link program: ${GLES20.glGetProgramInfoLog(glProgram)}")
        }

        aPositionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
        aTextureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord")
        uContentRatioHandle = GLES20.glGetUniformLocation(glProgram, "contentRatio")
        uViewRatioHandle = GLES20.glGetUniformLocation(glProgram, "viewRatio")

    }

    private fun initBuffers() {
        // 顶点坐标 (x, y, z)
        val vertices = floatArrayOf(
            -1.0f, -1.0f, 0f,
            1.0f, -1.0f, 0f,
            -1.0f, 1.0f, 0f,
            1.0f, 1.0f, 0f
        )

        val textureCoords = floatArrayOf(
            0.0f, 0.0f,  // 左下角
            1.0f, 0.0f,  // 右下角
            0.0f, 1.0f,  // 左上角
            1.0f, 1.0f   // 右上角
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

    fun bindFramebuffer() {
        // 尝试初始化 FBO 和输出纹理 (独立于输入类型，只在第一次渲染时执行)
        if (frameBuffers[0] == 0) {
            // 获取默认视口，以便在 finishDraw 中恢复
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, defaultViewport, 0)

            // --- 生成 FBO 附件纹理 (GL_TEXTURE_2D) ---
            GLES20.glGenTextures(F_BUFFER_CNT, frameBufferTextures, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextures[0])

            // 为 FBO 附件纹理分配存储空间
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGB,
                renderViewSize.width,
                renderViewSize.height,
                0,
                GLES20.GL_RGB,
                GLES20.GL_UNSIGNED_BYTE,
                null    // 不填充数据
            )

            // 设置纹理过滤参数
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0) // 解绑 FBO 附件纹理

            // --- 生成和绑定 FBO ---
            GLES20.glGenFramebuffers(F_BUFFER_CNT, frameBuffers, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0])

            // 将 FBO 附件纹理附加到 FBO
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                frameBufferTextures[0],
                0
            )
        } else {
            // FBO 已经初始化，直接绑定它
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0])
        }
    }

    fun useProgram() {
        GLES20.glUseProgram(glProgram)
        checkGLError("glUseProgram")
    }

    open fun beforeDraw(textureInfo: TextureInfo) {
        GLES20.glViewport(0, 0, renderViewSize.width, renderViewSize.height)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        checkGLError("beforeDraw")
    }

    protected fun onDraw(textureInfo: TextureInfo) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureInfo.textureType, textureInfo.textureID)

        GLES20.glUniform1f(uViewRatioHandle, renderViewSize.width.toFloat() / renderViewSize.height.toFloat())
        GLES20.glUniform1f(uContentRatioHandle, textureInfo.width.toFloat() / textureInfo.height.toFloat())

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTextureHandle)
        GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        //绘制调用，如果当前是FBO则像素数据写入到 FBO 纹理中，或者显示到屏幕上
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        checkGLError("renderFrame")
    }

    open fun afterDraw(textureInfo: TextureInfo) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        checkGLError("afterDraw")
    }

    open fun finishDraw(textureInfo: TextureInfo) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        // 恢复默认的视口
        GLES20.glViewport(defaultViewport[0], defaultViewport[1], defaultViewport[2], defaultViewport[3])
        checkGLError("glBindFramebuffer")
    }

    open fun getOutputTextureInfo(textureInfo: TextureInfo): TextureInfo {
        return TextureInfo(
            frameBufferTextures[0],
            GLES20.GL_TEXTURE_2D,
            renderViewSize.width,
            renderViewSize.height,
        )
    }

    abstract fun getFragmentShaderCode(textureInfo: TextureInfo): String
}