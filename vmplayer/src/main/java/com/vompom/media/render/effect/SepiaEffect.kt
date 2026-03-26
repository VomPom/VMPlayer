package com.vompom.media.render.effect

import com.vompom.media.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/12/15 21:14
 *
 * @Description 复古（怀旧）滤镜特效
 */
class SepiaEffect : BaseEffect() {
    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            
            void main() {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                
                // 复古滤镜公式
                float r = textureColor.r * 0.393 + textureColor.g * 0.769 + textureColor.b * 0.189;
                float g = textureColor.r * 0.349 + textureColor.g * 0.686 + textureColor.b * 0.168;
                float b = textureColor.r * 0.272 + textureColor.g * 0.534 + textureColor.b * 0.131;
                
                gl_FragColor = vec4(
                    min(r, 1.0),
                    min(g, 1.0),
                    min(b, 1.0),
                    textureColor.a
                );
            }
        """.trimIndent()
    }
}