package com.vompom.media.render.effect

import android.util.Size
import com.vompom.media.model.TextureInfo
import com.vompom.media.model.VideoEffectEntity
import com.vompom.media.render.effect.inner.RenderEffect
import com.vompom.media.render.effect.inner.TextureMatrixEffect
import java.util.concurrent.ConcurrentLinkedQueue

/**
 *
 * Created by @juliswang on 2025/12/02 20:49
 *
 * @Description 在渲染阶段的时候，对渲染的纹理进行效果处理
 */

class EffectGroup {
    private val filterQueue: ConcurrentLinkedQueue<BaseEffect?> = ConcurrentLinkedQueue<BaseEffect?>()
    private val textureMatrixEffect = TextureMatrixEffect()
    private var renderEffect: RenderEffect = RenderEffect()

    fun applyNewTexture(inputTexture: TextureInfo): TextureInfo {
        var textureInfo: TextureInfo = textureMatrixEffect.applyNewTexture(inputTexture)
        filterQueue.forEach {
            if (it != null) {
                textureInfo = it.applyNewTexture(textureInfo)
            }
        }
        return renderEffect.applyNewTexture(textureInfo)
    }

    fun addEffect(effect: BaseEffect) {
        filterQueue.add(effect)
    }

    fun removeEffect(key: Int) {
        filterQueue.firstOrNull { it?.hashCode() == key }?.let { filterQueue.remove(it) }
    }

    fun updateRenderViewSize(size: Size) {
        filterQueue.forEach {
            it?.updateRenderViewSize(size)
        }
        textureMatrixEffect.updateRenderViewSize(size)
        renderEffect.updateRenderViewSize(size)
    }

    companion object {
        fun createEffectGroup(entities: List<VideoEffectEntity>): EffectGroup {
            return EffectGroup().apply {
                this.filterQueue.addAll(entities.map { it.effectClz?.newInstance() })
            }
        }
    }
}