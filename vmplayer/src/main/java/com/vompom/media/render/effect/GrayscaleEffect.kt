package com.vompom.media.render.effect

import com.vompom.media.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/12/15 21:13
 *
 * @Description 灰度（黑白）滤镜特效
 */
class GrayscaleEffect : BaseEffect() {
    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            
            void main() {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                // 灰度转换公式：0.299*R + 0.587*G + 0.114*B
                float gray = textureColor.r * 0.299 + textureColor.g * 0.587 + textureColor.b * 0.114;
                gl_FragColor = vec4(gray, gray, gray, textureColor.a);
            }
        """.trimIndent()
    }
}