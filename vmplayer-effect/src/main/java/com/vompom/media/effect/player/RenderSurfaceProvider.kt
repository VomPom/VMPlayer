package com.vompom.media.effect.player

import android.util.Size
import android.view.Surface
import com.vompom.media.IRenderSurfaceProvider
import com.vompom.media.effect.IRenderSession
import com.vompom.media.effect.render.PlayerRender

/**
 *
 * Created by @juliswang on 2025/12/20 19:10
 *
 * @Description 持有 [PlayerView] + [PlayerRender]，把 OES Surface 的生命周期反向暴露给播放器核心。
 *              这样 [com.vompom.media.VMPlayer] 就无需直接依赖 OpenGL / PlayerView。
 */
class RenderSurfaceProvider(
    private val playerView: PlayerView,
    private val playerRender: PlayerRender,
    private val renderSession: IRenderSession
) : IRenderSurfaceProvider {

    init {
        // 绑定渲染链到 GL 线程
        renderSession.attachRenderChain(playerView.getGLThread(), playerRender)
    }

    override fun setRenderSize(size: Size) {
        playerView.setRenderSize(size)
        renderSession.updateRenderSize(size)
    }

    override fun setOnSurfaceReady(callback: (Surface) -> Unit) {
        playerRender.setSurfaceReadyCallback { surface ->
            callback(surface)
        }
    }

    override fun updateVideoSize(size: Size) {
        playerView.updateVideoSize(size)
    }

    override fun release() {
        playerView.release()
    }
}
