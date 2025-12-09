package com.vompom.media.utils

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import android.util.Size
import androidx.core.graphics.createBitmap
import com.vompom.media.model.TextureInfo
import com.vompom.media.model.VRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 *
 * Created by @juliswang on 2025/11/25 20:50
 *
 * @Description
 */

object GLUtils {
    fun createTexture(type: Int): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        val textureId = textures[0]
        GLES20.glBindTexture(type, textureId)
        checkGLError("glBindTexture mTextureID")

        GLES20.glTexParameterf(type, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(type, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(type, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        checkGLError("glTexParameter")
        return textureId
    }


    /**
     *  等比例对齐宽高（保留左右或者上下的黑边）
     *
     * @param surfaceSize   渲染 View的宽高
     * @param textureSize   目标纹理的宽高
     * @return
     */
    fun initGLViewportFit(surfaceSize: Size, textureSize: Size): VRect {
        val rHeight = textureSize.height
        val rWidth = textureSize.width

        val vHeight = surfaceSize.height
        val vWidth = surfaceSize.width

        var x = 0f
        var y = 0f
        var resultWidth: Float
        var resultHeight: Float

        // 计算宽高比
        val viewAspectRatio = vWidth / vHeight
        val renderAspectRatio = rWidth / rHeight

        // view的宽高比更大，view更扁、更宽
        if (viewAspectRatio > renderAspectRatio) {
            // 使用view的高度，等比例缩放宽度
            resultWidth = rWidth * vHeight / rHeight.toFloat()
            resultHeight = vHeight.toFloat()
            // x轴移动保持居中，y不变
            x = (vWidth - resultWidth) / 2f
        } else {
            // view更细长
            // 使用view的宽度，等比例缩放高度
            resultWidth = vWidth.toFloat()
            resultHeight = rHeight * vWidth / rWidth.toFloat()
            // y轴移动保持居中，x不变
            y = (vHeight - resultHeight) / 2f
        }
        return VRect(
            x = x,
            y = y,
            width = resultWidth.toInt(),
            height = resultHeight.toInt()
        )
    }

    /**
     * 保存纹理到bitmap，调试用
     *
     * @param textureInfo
     * @return
     */
    fun saveBitmap(textureInfo: TextureInfo): Bitmap {
        var textureInfo: TextureInfo = textureInfo

        val fbo = IntArray(1)
        GLES20.glGenFramebuffers(1, fbo, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textureInfo.textureID, 0
        )
        val pixelBuffer = ByteBuffer.allocateDirect(textureInfo.width * textureInfo.height * 4)
        pixelBuffer.order(ByteOrder.LITTLE_ENDIAN)
        pixelBuffer.rewind()
        GLES20.glReadPixels(
            0, 0, textureInfo.width, textureInfo.height, GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE, pixelBuffer
        )
        val bitmap = createBitmap(textureInfo.width, textureInfo.height, Bitmap.Config.ARGB_4444)
        pixelBuffer.rewind()
        bitmap.copyPixelsFromBuffer(pixelBuffer)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glDeleteFramebuffers(1, fbo, 0)
        return bitmap
    }

    @Throws(RuntimeException::class)
    fun checkGLError(msg: String?): Boolean {
        var failed = false
        var error: Int
        val errorContent = StringBuilder("")
        while ((EGL14.eglGetError().also { error = it }) != EGL14.EGL_SUCCESS) {
            glLogE(msg + ": EGL error: 0x" + Integer.toHexString(error))
            errorContent.append(msg + ": EGL error: 0x" + Integer.toHexString(error))
            failed = true
        }
        if (failed) {
            glLogE("GL check error failed: $errorContent")
        }
        return !failed
    }

    fun loadShader(type: Int, shaderCode: String): Int {
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

    private fun glLogE(msg: String?) = VLog.e("EGLHelper: " + msg + ", err=" + GLES20.glGetError())

}