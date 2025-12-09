package com.vompom.media.model

import android.util.Size

/**
 *
 * Created by @juliswang on 2025/11/27 20:51
 *
 * @Description
 */

class TextureInfo(
    val textureID: Int = -1,
    val textureType: Int,
    val width: Int,
    val height: Int,
    val preferRotation: Int = 0
) {
    val textureSize: Size = Size(width, height)
}