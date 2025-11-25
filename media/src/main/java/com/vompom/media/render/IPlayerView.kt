package com.vompom.media.render

import android.util.Size
import android.view.Surface

/**
 *
 * Created by @juliswang on 2025/11/20 21:36
 *
 * @Description
 */

interface IPlayerView {
    fun setSurfaceReadyCallback(callback: (Surface) -> Unit)
    fun setRenderSize(size: Size)
    fun updateVideoSize(size: Size)
    fun release()
}