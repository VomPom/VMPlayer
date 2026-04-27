package com.vompom.media.effect

import android.content.Context
import android.util.Size
import android.view.ViewGroup
import android.widget.FrameLayout
import com.vompom.media.IPlayer
import com.vompom.media.VMPlayer
import com.vompom.media.effect.export.ExporterFactory
import com.vompom.media.effect.player.PlayerView
import com.vompom.media.effect.player.RenderSurfaceProvider
import com.vompom.media.effect.render.PlayerRender

/**
 *
 * Created by @juliswang on 2025/12/20 19:15
 *
 * @Description VMPlayer 的高层组装入口，把以下四者串联起来：
 *              1. 核心播放器 [com.vompom.media.VMPlayer]
 *              2. OpenGL 渲染链 [PlayerRender] + [PlayerView]
 *              3. 渲染会话 [IRenderSession]（负责特效 / 贴纸）
 *              4. 导出器工厂 [ExporterFactory]（负责带特效的离屏合成）
 *
 *              对外使用者只需要提供承载 View 的 FrameLayout 和一个 [IRenderSession] 即可。
 */
object VMPlayerFactory {

    /** 默认渲染尺寸，可以被 [IPlayer.setRenderSize] 覆盖 */
    private const val DEFAULT_RENDER_WIDTH = 1280
    private const val DEFAULT_RENDER_HEIGHT = 720

    /**
     * 创建一个带 OpenGL 渲染链 + 特效能力的 [IPlayer] 实例。
     *
     * @param playerContainer   承载 [PlayerView] 的容器
     * @param renderSession     渲染会话，管理特效 / 贴纸
     * @param initialRenderSize 初始渲染尺寸（播放器会以此创建 FBO / Viewport）
     */
    fun create(
        playerContainer: FrameLayout,
        renderSession: IRenderSession,
        initialRenderSize: Size = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)
    ): IPlayer {
        val playerRender = createRender(initialRenderSize)
        val playerView = createPlayerView(playerContainer.context, playerRender, initialRenderSize)
        attachView(playerContainer, playerView)

        val surfaceProvider = RenderSurfaceProvider(playerView, playerRender, renderSession)
        val exporterFactory = ExporterFactory(renderSession)

        return VMPlayer.create(surfaceProvider, exporterFactory)
    }

    private fun createRender(size: Size): PlayerRender {
        return PlayerRender().apply {
            initRenderSize(size)
        }
    }

    private fun createPlayerView(context: Context, renderer: PlayerRender, size: Size): PlayerView {
        return PlayerView(context).apply {
            setRenderSize(size)
            setRenderer(renderer)
        }
    }

    private fun attachView(container: FrameLayout, playerView: PlayerView) {
        container.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}
