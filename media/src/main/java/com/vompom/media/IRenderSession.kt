package com.vompom.media

import android.util.Size
import com.vompom.media.model.RenderModel
import com.vompom.media.model.VideoEffectEntity
import com.vompom.media.render.IRendererEffect

/**
 *
 * Created by @juliswang on 2025/12/11 20:12
 *
 * @Description
 */

interface IRenderSession {
    fun updateRenderSize(size: Size)
    fun bindPlayer(player: IPlayer)
    fun addEffect(entity: VideoEffectEntity)
    fun removeEffect(entity: VideoEffectEntity)
    fun getRenderModel(): RenderModel
    // fixme:: 这个接口设计得不太友好，考虑整体结构调整干掉它
    fun attachRenderChain(glThread: IQueueEvent, renderer: IRendererEffect)
    fun flush()
}