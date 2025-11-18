package com.vompom.media.model

/**
 *
 * Created by @juliswang on 2025/10/24 20:47
 *
 * @Description 包含时间资源时间区间的 Asset 对象
 */

class ClipAsset(path: String, val sourceRange: TimeRange) : Asset(path) {

    init {
        checkRange()
    }

    /**
     * 使播放区间在资源可播放的区间内
     *
     */
    fun checkRange() {
        sourceRange.apply {
            if (sourceDurationUs <= 0) {
                startUs = 0
                durationUs = 0
                updateStartUs(startUs)
                return
            }

            if (durationUs < 0) {
                durationUs = 0
            }

            if (startUs < 0) {
                startUs = 0
            }

            if (startUs > sourceDurationUs) {
                startUs = sourceDurationUs
                durationUs = 0
            } else if (startUs <= Long.MAX_VALUE - durationUs && startUs + durationUs > sourceDurationUs) {
                durationUs = sourceDurationUs - startUs
            }

            // 6. 同步更新 endUs
            updateStartUs(startUs)
        }
    }
}