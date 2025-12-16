package com.vompom.media.render

import android.util.Size
import com.vompom.media.IPlayer
import com.vompom.media.IQueueEvent
import com.vompom.media.IRenderSession
import com.vompom.media.VMPlayer.Companion.DEFAULT_RENDER_HEIGHT
import com.vompom.media.VMPlayer.Companion.DEFAULT_RENDER_WIDTH
import com.vompom.media.model.RenderModel
import com.vompom.media.model.VideoEffectEntity

/**
 *
 * Created by @juliswang on 2025/12/12 10:30
 *
 * @Description 对外暴露渲染链的接口
 */

class VMRenderSession : IRenderSession {
    private val renderChain = EffectChainManager()
    private var player: IPlayer? = null
    private var renderSize = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)

    private var glThread: IQueueEvent? = null
    private var renderer: IRendererEffect? = null

    private constructor()

    companion object {
        fun createRenderSession(): IRenderSession = VMRenderSession()
    }

    override fun attachRenderChain(glThread: IQueueEvent, renderer: IRendererEffect) {
        this.glThread = glThread
        this.renderer = renderer

        runInGlThread {
            renderChain.bindRenderer(renderer)
        }
    }

    override fun updateRenderSize(size: Size) {
        this.renderSize = size
        this.renderChain.updateRenderSize(size)
    }

    override fun bindPlayer(player: IPlayer) {
        this.player = player
    }

    override fun addEffect(entity: VideoEffectEntity) {
        runInGlThread {
            renderChain.addEffect(entity)
        }
    }

    override fun removeEffect(entity: VideoEffectEntity) {
        runInGlThread {
            renderChain.removeEffect(entity)
        }
    }

    override fun getRenderModel(): RenderModel = RenderModel(
        renderSize,
        renderChain.getEffectEntities()
    )

    override fun flush() {
        // todo:: 这里希望的是未来有一些不用立马实现的特效，待flush之后再统一进行处理，提升整体的性能
    }

    fun runInGlThread(runnable: Runnable) {
        this.glThread?.queueEvent(runnable)
    }
}