package com.vompom.media.model

import com.vompom.media.docode.decorder.IDecoder

/**
 *
 * Created by @juliswang on 2025/10/20 16:14
 *
 * @Description
 */
class SampleState {
    var frameTimeUs: Long = 0
    var statusCode = 0
    var msg = ""

    constructor() : this(0)

    constructor(frameTimeUs: Long, status: Int = IDecoder.SAMPLE_STATE_NORMAL, msg: String = "") {
        this.frameTimeUs = frameTimeUs
        this.statusCode = status
        this.msg = msg
    }

    companion object {
        fun byError(code: Int = IDecoder.SAMPLE_STATE_ERROR, msg: String = ""): SampleState {
            return SampleState(-1, code, msg)
        }
    }


    override fun toString(): String {
        return "[timeUs:$frameTimeUs, state:$statusCode]"
    }
}
