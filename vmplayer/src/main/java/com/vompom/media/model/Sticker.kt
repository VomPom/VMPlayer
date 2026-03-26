package com.vompom.media.model

/**
 *
 * Created by @juliswang on 2025/12/19 16:13
 *
 * @Description
 */

data class Sticker(
    val assetPath: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val type: String
)