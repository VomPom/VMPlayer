package com.vompom.media.effect.render.effect

import com.vompom.media.effect.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/11/27 20:50
 *
 * @Description
 */

interface IFilter {
    fun applyNewTexture(inputTextureInfo: TextureInfo): TextureInfo
}
