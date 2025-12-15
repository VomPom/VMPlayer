package com.vompom.media

import com.vompom.media.render.effect.BaseEffect

/**
 *
 * Created by @juliswang on 2025/12/11 20:12
 *
 * @Description
 */

interface IRenderSession {
    fun bindPlayer(player: IPlayer)
    fun addEffect(effect: BaseEffect)
    fun removeEffect(effect: BaseEffect)
}