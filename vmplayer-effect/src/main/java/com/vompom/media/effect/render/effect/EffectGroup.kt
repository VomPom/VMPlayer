package com.vompom.media.effect.render.effect

import android.util.Size
import com.vompom.media.effect.model.TextureInfo
import com.vompom.media.effect.model.VideoEffectEntity
import com.vompom.media.effect.render.effect.inner.RenderEffect
import com.vompom.media.effect.render.effect.inner.TextureMatrixEffect
import com.vompom.media.effect.render.sticker.StickerEffect
import java.util.concurrent.ConcurrentLinkedQueue

/**
 *
 * Created by @juliswang on 2025/12/02 20:49
 *
 * @Description 在渲染阶段的时候，对渲染的纹理进行效果处理
 */

class EffectGroup {
    private val filterQueue: ConcurrentLinkedQueue<BaseEffect?> = ConcurrentLinkedQueue<BaseEffect?>()
    private val stickerQueue: ConcurrentLinkedQueue<StickerEffect> = ConcurrentLinkedQueue<StickerEffect>()
    private val textureMatrixEffect = TextureMatrixEffect()
    private var renderEffect: RenderEffect = RenderEffect()

    fun applyNewTexture(inputTexture: TextureInfo): TextureInfo {
        var textureInfo: TextureInfo = textureMatrixEffect.applyNewTexture(inputTexture)
        filterQueue.forEach {
            if (it != null) {
                textureInfo = it.applyNewTexture(textureInfo)
            }
        }
        // 依次应用所有贴纸效果
        stickerQueue.forEach { sticker ->
            textureInfo = sticker.applyNewTexture(textureInfo)
        }
        return renderEffect.applyNewTexture(textureInfo)
    }

    fun addEffect(effect: BaseEffect) {
        filterQueue.add(effect)
    }

    fun removeEffect(key: Int) {
        filterQueue.firstOrNull { it?.hashCode() == key }?.let { filterQueue.remove(it) }
    }

    /**
     * 添加贴纸效果
     */
    fun addSticker(sticker: StickerEffect) {
        stickerQueue.add(sticker)
    }

    /**
     * 根据 stickerId 移除贴纸效果
     */
    fun removeSticker(stickerId: Long) {
        stickerQueue.firstOrNull { it.stickerId == stickerId }?.let {
            stickerQueue.remove(it)
            it.release()
        }
    }

    /**
     * 移除所有贴纸效果
     */
    fun clearStickers() {
        stickerQueue.forEach { it.release() }
        stickerQueue.clear()
    }

    /**
     * 获取当前贴纸数量
     */
    fun getStickerCount(): Int = stickerQueue.size

    /**
     * 获取当前所有贴纸的克隆副本（用于导出等需要在新 GL 上下文中重建的场景）
     */
    fun cloneStickers(): List<StickerEffect> {
        return stickerQueue.map { it.cloneSticker() }
    }

    fun updateRenderViewSize(size: Size) {
        filterQueue.forEach {
            it?.updateRenderViewSize(size)
        }
        stickerQueue.forEach {
            it.updateRenderViewSize(size)
        }
        textureMatrixEffect.updateRenderViewSize(size)
        renderEffect.updateRenderViewSize(size)
    }

    companion object {
        fun createEffectGroup(
            entities: List<VideoEffectEntity>,
            stickers: List<StickerEffect> = emptyList()
        ): EffectGroup {
            return EffectGroup().apply {
                this.filterQueue.addAll(entities.map { it.effectClz?.newInstance() })
                this.stickerQueue.addAll(stickers)
            }
        }
    }
}
