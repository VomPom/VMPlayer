package com.vompom.media.render.effect

import android.opengl.GLES20
import com.vompom.media.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/11/27 21:14
 *
 * @Description 实现对 RGB 三个颜色通道的改变
 */

class RGBEffect : BaseEffect() {
    private var redLocation = 0
    private var greenLocation = 0
    private var blueLocation = 0

    private var red = 1f
    private var green = 1f
    private var blue = 1f

    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform float red;
            uniform float green;
            uniform float blue;
            uniform sampler2D sTexture;
            void main() {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                gl_FragColor = vec4(textureColor.r * red, textureColor.g * green, textureColor.b * blue, 1.0);
            }
        """.trimIndent()
    }

    override fun initShader(inputTexture: TextureInfo) {
        super.initShader(inputTexture)

        redLocation = GLES20.glGetUniformLocation(glProgram, "red")
        greenLocation = GLES20.glGetUniformLocation(glProgram, "green")
        blueLocation = GLES20.glGetUniformLocation(glProgram, "blue")
    }

    override fun beforeDraw(textureInfo: TextureInfo) {
        super.beforeDraw(textureInfo)

        GLES20.glUniform1f(redLocation, red)
        GLES20.glUniform1f(greenLocation, green)
        GLES20.glUniform1f(blueLocation, blue)
    }

    fun setRed(value: Float): RGBEffect {
        this.red = value
        return this
    }

    fun setGreen(value: Float): RGBEffect {
        this.green = value
        return this
    }

    fun setBlue(value: Float): RGBEffect {
        this.blue = value
        return this
    }

    fun setRGB(r: Float, g: Float, b: Float): RGBEffect {
        this.red = r
        this.green = g
        this.blue = b
        return this
    }
}