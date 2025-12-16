package com.vompom.media.render

import android.util.Size
import com.vompom.media.VMPlayer.Companion.DEFAULT_RENDER_HEIGHT
import com.vompom.media.VMPlayer.Companion.DEFAULT_RENDER_WIDTH
import com.vompom.media.model.VideoEffectEntity
import com.vompom.media.render.effect.EffectGroup
import java.util.concurrent.ConcurrentLinkedQueue

/**
 *
 * Created by @juliswang on 2025/12/16 20:52
 *
 * @Description
 */

class EffectChainManager : IEffectChain {
    private var effectGroup: EffectGroup? = null
    private var renderer: IRendererEffect? = null
    private var renderSize = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)
    private val effectEntities = ConcurrentLinkedQueue<VideoEffectEntity>()

    fun bindRenderer(renderer: IRendererEffect) {
        this.renderer = renderer
        EffectGroup().apply {
            effectGroup = this
            renderer.setEffectGroup(this)
        }
    }

    override fun addEffect(entity: VideoEffectEntity) {
        entity.effectClz?.let {
            val effect = it.newInstance()
            entity.key = effect.hashCode()
            effect.updateRenderViewSize(renderSize)
            effectGroup?.addEffect(effect)
        }
        effectEntities.add(entity)
    }

    override fun removeEffect(entity: VideoEffectEntity) {
        entity.key?.let {
            effectGroup?.removeEffect(it)
        }
        effectEntities.remove(entity)
    }

    override fun updateRenderSize(size: Size) {
        this.renderSize = size
    }

    override fun getEffectEntities(): List<VideoEffectEntity> = effectEntities.toList()
}

interface IEffectChain {
    fun addEffect(entity: VideoEffectEntity)
    fun removeEffect(entity: VideoEffectEntity)
    fun getEffectEntities(): List<VideoEffectEntity>
    fun updateRenderSize(size: Size)
}