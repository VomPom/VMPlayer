package com.vompom.media.render.effect

import android.util.Size
import com.vompom.media.model.TextureInfo
import com.vompom.media.render.effect.inner.RenderEffect
import com.vompom.media.render.effect.inner.TextureMatrixEffect
import java.util.LinkedList

/**
 *
 * Created by @juliswang on 2025/12/02 20:49
 *
 * @Description
 */

class EffectGroup {
    private val filterLinkedList: LinkedList<BaseEffect?> = LinkedList<BaseEffect?>()
    private val textureMatrixEffect = TextureMatrixEffect()
    private var renderEffect: RenderEffect? = null

    /**
     * 构造
     *
     * @param render 是否上屏渲染，true 添加上屏 false 不上屏只做 Effect
     */
    constructor(render: Boolean) {
        if (render) {
            renderEffect = RenderEffect()
        }
    }

    fun applyNewTexture(inputTexture: TextureInfo) {
        var textureInfo: TextureInfo = textureMatrixEffect.applyNewTexture(inputTexture)
        filterLinkedList.forEach {
            if (it != null) {
                textureInfo = it.applyNewTexture(textureInfo)
            }
        }
        if (renderEffect != null) {
            renderEffect?.applyNewTexture(textureInfo)
        }
    }

    fun addEffect(effect: BaseEffect) {
        filterLinkedList.add(effect)
    }

    fun updateRenderViewSize(size: Size) {
        filterLinkedList.forEach {
            it?.updateRenderViewSize(size)
        }
        textureMatrixEffect.updateRenderViewSize(size)
        renderEffect?.updateRenderViewSize(size)
    }

}