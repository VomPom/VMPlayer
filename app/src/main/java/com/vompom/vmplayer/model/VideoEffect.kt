package com.vompom.vmplayer.model

import com.vompom.media.render.effect.BaseEffect
import com.vompom.media.render.effect.GrayscaleEffect
import com.vompom.media.render.effect.InvertEffect
import com.vompom.media.render.effect.RGBEffect
import com.vompom.media.render.effect.SepiaEffect

/**
 * 特效类型枚举
 */
enum class EffectType {
    NONE,           // 无特效
    GRAYSCALE,      // 黑白滤镜
    SEPIA,          // 复古滤镜
    BLUR,           // 模糊效果
    SHARPEN,        // 锐化效果
    INVERT,         // 反相效果
    RGB_ADJUST      // RGB调整
}

/**
 * 视频特效数据类
 * @param type 特效类型
 * @param name 特效显示名称
 * @param effect 特效实现对象（可为空，需要时动态创建）
 */
data class VideoEffect(
    val type: EffectType,
    val name: String,
    var effect: BaseEffect? = null,
    var isSelected: Boolean = false
) {
    companion object {
        /**
         * 获取默认的特效列表
         */
        fun getDefaultEffects(): List<VideoEffect> = listOf(
            VideoEffect(EffectType.NONE, "无特效", null),
            VideoEffect(EffectType.GRAYSCALE, "黑白滤镜", GrayscaleEffect()),
            VideoEffect(EffectType.SEPIA, "复古滤镜", SepiaEffect()),
            VideoEffect(EffectType.BLUR, "模糊效果"),
            VideoEffect(EffectType.SHARPEN, "锐化效果"),
            VideoEffect(EffectType.INVERT, "反相效果", InvertEffect()),
            VideoEffect(EffectType.RGB_ADJUST, "RGB调整", RGBEffect())
        )
    }
}