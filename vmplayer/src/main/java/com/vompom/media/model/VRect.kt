package com.vompom.media.model

import android.graphics.PointF
import android.util.Size

/**
 *
 * Created by @juliswang on 2025/12/05 11:05
 *
 * @Description
 */

class VRect(
    x: Float,
     y: Float,
    var width: Int,
    var height: Int
) {
    val origin: PointF = PointF(x, y)
    var size: Size = Size(width, height)
}