package com.vompom.media.utils

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.GLES20
import androidx.core.graphics.createBitmap
import com.vompom.media.model.TextureInfo
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