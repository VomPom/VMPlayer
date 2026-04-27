package com.vompom.media.effect.render

import android.util.Size
import com.vompom.media.effect.model.VideoEffectEntity
import com.vompom.media.effect.render.effect.EffectGroup
import com.vompom.media.effect.render.sticker.StickerEffect
import java.util.concurrent.ConcurrentLinkedQueue

/**
 *
 * Created by @juliswang on 2025/12/16 20:52
 *
 * @Description
 */

class EffectChainManager : IEffectChain {
    companion object {
        private const val DEFAULT_RENDER_WIDTH = 1280
        private const val DEFAULT_RENDER_HEIGHT = 720
    }

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

    /**
     * 添加贴纸效果
     */
    fun addSticker(sticker: StickerEffect) {
        sticker.updateRenderViewSize(renderSize)
        effectGroup?.addSticker(sticker)
    }

    /**
     * 根据 stickerId 移除贴纸效果
     */
    fun removeSticker(stickerId: Long) {
        effectGroup?.removeSticker(stickerId)
    }

    /**
     * 移除所有贴纸
     */
    fun clearStickers() {
        effectGroup?.clearStickers()
    }

    /**
     * 获取当前贴纸数量
     */
    fun getStickerCount(): Int = effectGroup?.getStickerCount() ?: 0

    /**
     * 获取当前所有贴纸的克隆副本（用于导出等需要在新 GL 上下文中重建的场景）
     */
    fun cloneStickers(): List<StickerEffect> = effectGroup?.cloneStickers() ?: emptyList()
}

interface IEffectChain {
    fun addEffect(entity: VideoEffectEntity)
    fun removeEffect(entity: VideoEffectEntity)
    fun getEffectEntities(): List<VideoEffectEntity>
    fun updateRenderSize(size: Size)
}
