package com.vompom.media.render.effect

import com.vompom.media.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/12/02 20:26
 *
 * @Description 实现 反相 的效果
 */

class InvertEffect : BaseEffect() {
    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main()
            {
                vec4 textureColor = texture2D(sTexture, vTextureCoord);
                gl_FragColor = vec4((1.0 - textureColor.rgb), textureColor.w);
            }
      """.trimIndent()
    }
}