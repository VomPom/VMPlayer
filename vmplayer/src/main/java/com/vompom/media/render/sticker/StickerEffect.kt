package com.vompom.media.render.sticker

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.vompom.media.model.TextureInfo
import com.vompom.media.render.effect.BaseEffect

/**
 *
 * Created by @juliswang on 2025/12/19 16:21
 *
 * @Description 贴纸特效，支持在视频画面上叠加贴纸图片
 *              支持通过文件路径或 Bitmap 创建，可配置位置、大小和透明度
 */

class StickerEffect : BaseEffect {

    /** 贴纸唯一标识，用于添加/移除管理 */
    val stickerId: Long

    private var bitmap: Bitmap? = null
    private var stickerTextureHandle = 0
    private var stickerTextureID: Int = -1
    private var uStickerPositionLocation = -1
    private var uStickerSizeLocation = -1
    private var uStickerAlphaLocation = -1

    /** 贴纸位置（标准化坐标 0~1，左下角为原点） */
    var positionX: Float = 0.7f
    var positionY: Float = 0.7f

    /** 贴纸大小（标准化坐标 0~1，相对于画面宽高的比例） */
    var stickerWidth: Float = 0.2f
    var stickerHeight: Float = 0.2f

    /** 贴纸透明度 0~1 */
    var alpha: Float = 1.0f

    /** 贴纸图片原始宽高比（宽/高），用于保持贴纸不变形 */
    private var stickerAspectRatio: Float = 1.0f

    /**
     * 通过文件路径创建贴纸
     */
    constructor(path: String) : super() {
        this.stickerId = System.nanoTime()
        this.bitmap = BitmapFactory.decodeFile(path)
        updateAspectRatio()
    }

    /**
     * 通过 Bitmap 创建贴纸
     */
    constructor(bitmap: Bitmap) : super() {
        this.stickerId = System.nanoTime()
        this.bitmap = bitmap
        updateAspectRatio()
    }

    /**
     * 通过文件路径创建贴纸，并指定位置和大小
     */
    constructor(
        path: String,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        alpha: Float = 1.0f
    ) : super() {
        this.stickerId = System.nanoTime()
        this.bitmap = BitmapFactory.decodeFile(path)
        this.positionX = posX
        this.positionY = posY
        this.stickerWidth = width
        this.stickerHeight = height
        this.alpha = alpha
        updateAspectRatio()
    }

    /**
     * 通过 Bitmap 创建贴纸，并指定位置和大小
     */
    constructor(
        bitmap: Bitmap,
        posX: Float,
        posY: Float,
        width: Float,
        height: Float,
        alpha: Float = 1.0f
    ) : super() {
        this.stickerId = System.nanoTime()
        this.bitmap = bitmap
        this.positionX = posX
        this.positionY = posY
        this.stickerWidth = width
        this.stickerHeight = height
        this.alpha = alpha
        updateAspectRatio()
    }

    /**
     * 根据 Bitmap 实际尺寸计算宽高比
     */
    private fun updateAspectRatio() {
        bitmap?.let {
            if (it.height > 0) {
                stickerAspectRatio = it.width.toFloat() / it.height.toFloat()
            }
        }
    }

    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;

            uniform sampler2D sTexture; 
            uniform sampler2D uStickerTexture; 
            uniform vec2 uStickerPosition;  // 贴纸位置（标准化坐标）
            uniform vec2 uStickerSize;      // 贴纸大小（已根据宽高比校正）
            uniform float uStickerAlpha;    // 贴纸透明度

            void main() {
                vec4 videoColor = texture2D(sTexture, vTextureCoord);
                vec2 stickerCoord = (vTextureCoord - uStickerPosition) / uStickerSize;
                // 翻转 Y 轴：Bitmap 像素数据 Y=0 在顶部，OpenGL 纹理坐标 Y=0 在底部
                stickerCoord.y = 1.0 - stickerCoord.y;
                vec4 stickerColor = texture2D(uStickerTexture, stickerCoord);
                
                if(stickerCoord.x >= 0.0 && stickerCoord.x <= 1.0 && 
                   stickerCoord.y >= 0.0 && stickerCoord.y <= 1.0 && stickerColor.a > 0.0) {
                    float alpha = stickerColor.a * uStickerAlpha;
                    gl_FragColor = mix(videoColor, stickerColor, alpha);
                } else {
                   gl_FragColor = videoColor;
                }
            }
        """.trimIndent()
    }

    override fun initShader(inputTexture: TextureInfo) {
        super.initShader(inputTexture)
        stickerTextureHandle = GLES20.glGetUniformLocation(glProgram, "uStickerTexture")
        uStickerPositionLocation = GLES20.glGetUniformLocation(glProgram, "uStickerPosition")
        uStickerSizeLocation = GLES20.glGetUniformLocation(glProgram, "uStickerSize")
        uStickerAlphaLocation = GLES20.glGetUniformLocation(glProgram, "uStickerAlpha")

        if (stickerTextureID == -1) {
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            stickerTextureID = textures[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTextureID)

            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // 加载贴纸纹理数据
            if (bitmap != null && !bitmap!!.isRecycled) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            } else {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES20.GL_RGBA,
                    bitmap?.width ?: 0,
                    bitmap?.height ?: 0,
                    0,
                    GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE,
                    null
                )
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }

    override fun beforeDraw(textureInfo: TextureInfo) {
        super.beforeDraw(textureInfo)

        // 使用纹理单元1来绑定贴纸纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stickerTextureID)
        GLES20.glUniform1i(stickerTextureHandle, 1)

        GLES20.glUniform2f(uStickerPositionLocation, positionX, positionY)
        // 使用输入纹理的实际尺寸计算宽高比，而非 renderViewSize
        // 因为第一个贴纸的输入纹理可能来自视频原始尺寸，经过 viewport fit 后
        // 实际渲染区域的宽高比与 renderViewSize 不一定一致
        val texWidth = textureInfo.textureSize.width.toFloat()
        val texHeight = textureInfo.textureSize.height.toFloat()
        val viewAspect = if (texHeight > 0) texWidth / texHeight else 1.0f
        val correctedHeight = stickerWidth / stickerAspectRatio * viewAspect
        GLES20.glUniform2f(uStickerSizeLocation, stickerWidth, correctedHeight)
        GLES20.glUniform1f(uStickerAlphaLocation, alpha)
    }

    /**
     * 克隆贴纸效果（用于导出等需要在新 GL 上下文中重建的场景）
     * 会复制 Bitmap 和所有参数，但不会复制 GL 资源（需要在新 GL 上下文中重新初始化）
     */
    fun cloneSticker(): StickerEffect {
        val bitmapCopy = bitmap?.copy(bitmap!!.config, true)
        return StickerEffect(
            bitmap = bitmapCopy ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            posX = positionX,
            posY = positionY,
            width = stickerWidth,
            height = stickerHeight,
            alpha = alpha
        )
    }

    /**
     * 释放贴纸纹理资源
     */
    fun release() {
        if (stickerTextureID != -1) {
            GLES20.glDeleteTextures(1, intArrayOf(stickerTextureID), 0)
            stickerTextureID = -1
        }
        bitmap?.recycle()
        bitmap = null
    }
}