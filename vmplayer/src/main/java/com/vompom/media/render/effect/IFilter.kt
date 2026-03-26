package com.vompom.media.render.effect

import com.vompom.media.model.TextureInfo

/**
 *
 * Created by @juliswang on 2025/11/27 20:50
 *
 * @Description
 */

interface IFilter {
    fun applyNewTexture(inputTextureInfo: TextureInfo): TextureInfo
}