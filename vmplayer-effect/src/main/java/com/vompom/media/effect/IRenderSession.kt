package com.vompom.media.effect

import android.util.Size
import com.vompom.media.IPlayer
import com.vompom.media.effect.model.RenderModel
import com.vompom.media.effect.model.VideoEffectEntity
import com.vompom.media.effect.render.IRendererEffect
import com.vompom.media.effect.render.sticker.StickerEffect

/**
 *
 * Created by @juliswang on 2025/12/11 20:12
 *
 * @Description 对外暴露的渲染链会话接口，负责特效 / 贴纸 / RenderChain 的管理
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

    /** 添加贴纸 */
    fun addSticker(sticker: StickerEffect)

    /** 根据 stickerId 移除贴纸 */
    fun removeSticker(stickerId: Long)

    /** 移除所有贴纸 */
    fun clearStickers()
}
