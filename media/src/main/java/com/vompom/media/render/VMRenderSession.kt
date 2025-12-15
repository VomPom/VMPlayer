package com.vompom.media.render

import com.vompom.media.IPlayer
import com.vompom.media.IRenderSession
import com.vompom.media.render.effect.BaseEffect

/**
 *
 * Created by @juliswang on 2025/12/12 10:30
 *
 * @Description
 */

class VMRenderSession : IRenderSession {
    constructor()

    companion object {
        fun createVMSession(): IRenderSession {
            return VMRenderSession()
        }
    }

    private var player: IPlayer? = null
    override fun bindPlayer(player: IPlayer) {
        this.player = player
    }

    override fun addEffect(effect: BaseEffect) {
    }

    override fun removeEffect(effect: BaseEffect) {
    }


}