package com.vompom.media.render.effect.inner

import android.opengl.GLES20
import com.vompom.media.model.TextureInfo
import com.vompom.media.render.effect.BaseEffect
import com.vompom.media.utils.GLUtils

/**
 *
 * Created by @juliswang on 2025/12/02 21:11
 *
 * @Description 专门用于上屏的特效节点
 */

class RenderEffect : BaseEffect() {
    override fun getFragmentShaderCode(textureInfo: TextureInfo): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main()
            {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
      """.trimIndent()
    }

    override fun beforeDraw(textureInfo: TextureInfo) {
        super.beforeDraw(textureInfo)


        //  绑定到屏幕 (这是最后一个 Effect)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLUtils.checkGLError("RGBEffect beforeDraw")
    }

}