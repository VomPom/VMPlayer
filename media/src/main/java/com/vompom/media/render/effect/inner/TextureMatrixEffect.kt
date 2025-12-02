package com.vompom.media.render.effect.inner

import com.vompom.media.model.TextureInfo
import com.vompom.media.render.effect.BaseEffect

/**
 *
 * Created by @juliswang on 2025/11/27 21:11
 *
 * @Description 专门负责对输入的纹理和定点的方向做变换处理
 */

class TextureMatrixEffect : BaseEffect() {
    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                // 来自 OES 纹理的坐标（原点位于左上角）与 OpenGL 纹理的坐标不同(原点位于左下角)，这里需要进行转换
                gl_FragColor = texture2D(sTexture, vec2(vTextureCoord.x, 1.0 - vTextureCoord.y));
            }
        """
    }
}