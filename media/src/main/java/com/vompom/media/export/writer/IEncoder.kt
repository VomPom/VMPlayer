package com.vompom.media.export.writer

import android.media.MediaFormat

/**
 *
 * Created by @juliswang on 2025/11/05 16:59
 *
 * @Description
 */

interface IEncoder {
    fun prepare()
    fun finishEncoding()
    fun encodeType(): String
    fun release()
    fun listen(
        onFormatChange: (format: MediaFormat) -> Int,
        onBufferEncode: OnBufferEncode
    )
}