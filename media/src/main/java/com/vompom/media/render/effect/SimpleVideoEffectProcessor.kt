package com.vompom.media.render.effect

import android.opengl.GLES20
import android.util.Size
import com.vompom.media.render.VideoEffectProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 简单的视频特效处理器
 *
 * 支持基础的颜色调整、滤镜等效果
 */
class SimpleVideoEffectProcessor : VideoEffectProcessor {

    // 特效参数
    private var brightness = 0.0f  // 亮度调整 [-1.0, 1.0]
    private var contrast = 1.0f    // 对比度调整 [0.0, 2.0]
    private var saturation = 1.0f  // 饱和度调整 [0.0, 2.0]
    private var hue = 0.0f         // 色相调整 [-180, 180]

    // OpenGL相关
    private var effectProgram = 0
    private var frameBuffer = 0
    private var frameTexture = 0
    private var vertexBuffer: FloatBuffer? = null
    private var textureBuffer: FloatBuffer? = null

    private var isInitialized = false

    companion object {
        // 特效着色器
        private const val EFFECT_VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val EFFECT_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHue;
            
            // RGB转HSV
            vec3 rgb2hsv(vec3 c) {
                vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
                vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
                vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
                float d = q.x - min(q.w, q.y);
                float e = 1.0e-10;
                return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
            }
            
            // HSV转RGB
            vec3 hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }
            
            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);
                
                // 亮度调整
                color.rgb += uBrightness;
                
                // 对比度调整
                color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
                
                // 饱和度和色相调整
                vec3 hsv = rgb2hsv(color.rgb);
                hsv.y *= uSaturation;
                hsv.x += uHue / 360.0;
                color.rgb = hsv2rgb(hsv);
                
                // 确保颜色值在有效范围内
                color.rgb = clamp(color.rgb, 0.0, 1.0);
                
                gl_FragColor = color;
            }
        """
    }

    init {
        setupBuffers()
    }

    override fun processFrame(textureId: Int, videoSize: Size) {
        if (hasEffects()) {
            if (!isInitialized) {
                initialize(videoSize)
            }
            applyEffects(textureId, videoSize)
        }
    }

    private fun hasEffects(): Boolean {
        return brightness != 0.0f || contrast != 1.0f || saturation != 1.0f || hue != 0.0f
    }

    private fun initialize(videoSize: Size) {
        setupShaders()
        setupFrameBuffer(videoSize)
        isInitialized = true
    }

    private fun setupShaders() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, EFFECT_VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, EFFECT_FRAGMENT_SHADER)

        effectProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(effectProgram, vertexShader)
        GLES20.glAttachShader(effectProgram, fragmentShader)
        GLES20.glLinkProgram(effectProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(effectProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw RuntimeException("Could not link effect program: ${GLES20.glGetProgramInfoLog(effectProgram)}")
        }
    }

    private fun setupFrameBuffer(videoSize: Size) {
        // 创建帧缓冲和纹理
        val frameBuffers = IntArray(1)
        val textures = IntArray(1)

        GLES20.glGenFramebuffers(1, frameBuffers, 0)
        GLES20.glGenTextures(1, textures, 0)

        frameBuffer = frameBuffers[0]
        frameTexture = textures[0]

        // 设置纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            videoSize.width, videoSize.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // 绑定到帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, frameTexture, 0
        )
    }

    private fun setupBuffers() {
        // 顶点坐标
        val vertices = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
        )

        // 纹理坐标
        val textureCoords = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer?.position(0)

        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureBuffer?.position(0)
    }

    private fun applyEffects(textureId: Int, videoSize: Size) {
        // 绑定帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GLES20.glViewport(0, 0, videoSize.width, videoSize.height)

        // 使用特效着色器
        GLES20.glUseProgram(effectProgram)

        // 设置uniform参数
        val brightnessHandle = GLES20.glGetUniformLocation(effectProgram, "uBrightness")
        val contrastHandle = GLES20.glGetUniformLocation(effectProgram, "uContrast")
        val saturationHandle = GLES20.glGetUniformLocation(effectProgram, "uSaturation")
        val hueHandle = GLES20.glGetUniformLocation(effectProgram, "uHue")

        GLES20.glUniform1f(brightnessHandle, brightness)
        GLES20.glUniform1f(contrastHandle, contrast)
        GLES20.glUniform1f(saturationHandle, saturation)
        GLES20.glUniform1f(hueHandle, hue)

        // 绑定输入纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // 设置顶点属性
        val positionHandle = GLES20.glGetAttribLocation(effectProgram, "aPosition")
        val textureHandle = GLES20.glGetAttribLocation(effectProgram, "aTextureCoord")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(textureHandle)
        GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // 恢复默认帧缓冲
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
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

    // 特效参数设置方法
    fun setBrightness(brightness: Float) {
        this.brightness = brightness.coerceIn(-1.0f, 1.0f)
    }

    fun setContrast(contrast: Float) {
        this.contrast = contrast.coerceIn(0.0f, 2.0f)
    }

    fun setSaturation(saturation: Float) {
        this.saturation = saturation.coerceIn(0.0f, 2.0f)
    }

    fun setHue(hue: Float) {
        this.hue = hue.coerceIn(-180f, 180f)
    }

    fun release() {
        if (effectProgram != 0) {
            GLES20.glDeleteProgram(effectProgram)
        }
        if (frameBuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(frameBuffer), 0)
        }
        if (frameTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(frameTexture), 0)
        }
    }
}