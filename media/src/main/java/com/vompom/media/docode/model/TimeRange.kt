package com.vompom.media.docode.model

import com.vompom.media.utils.sToUs

/**
 *
 * Created by @juliswang on 2025/10/24 20:38
 *
 * @Description todo:: use time scale to eliminate error.
 */

class TimeRange(var startUs: Long = 0L, var durationUs: Long = 0L) {
    var endUs = 0L

    companion object {
        fun create(startS: Float, durationS: Float): TimeRange {
            return TimeRange(sToUs(startS).toLong(), sToUs(durationS).toLong())
        }
    }

    fun updateStartUs(startUs: Long) {
        this.startUs = startUs
        this.endUs = (this.startUs + this.durationUs)
    }
}