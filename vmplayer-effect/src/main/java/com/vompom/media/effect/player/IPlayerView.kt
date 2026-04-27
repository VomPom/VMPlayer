package com.vompom.media.effect.player

import android.util.Size

/**
 *
 * Created by @juliswang on 2025/11/20 21:36
 *
 * @Description
 */

interface IPlayerView {
    fun setRenderSize(size: Size)
    fun updateVideoSize(size: Size)
    fun release()
}
