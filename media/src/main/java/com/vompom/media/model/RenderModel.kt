package com.vompom.media.model

import android.util.Size
import com.vompom.media.render.effect.BaseEffect

/**
 *
 * Created by @juliswang on 2025/12/16 21:07
 *
 * @Description 记录渲染过程中所需要的各种数据
 */

data class RenderModel(
    val renderSize: Size,
    val effectList: List<VideoEffectEntity> = emptyList<VideoEffectEntity>(),
)

//todo::未来可以将所有的特效（包括音频/贴纸等）抽象成一个 Entity
/**
 * 视频特效数据类
 * @param type 特效类型
 * @param name 特效显示名称
 * @param effectClz 特效实现对象（可为空，需要时动态创建）
 */
data class VideoEffectEntity(
    val type: EffectType,
    val name: String,
    var effectClz: Class<out BaseEffect>? = null,
    var isSelected: Boolean = false,
    var key: Int? = -1
)

/**
 * 特效类型枚举
 */
enum class EffectType {
    NONE,           // 无特效
    GRAYSCALE,      // 黑白滤镜
    SEPIA,          // 复古滤镜
    INVERT,         // 反相效果
    RGB_ADJUST      // RGB调整
}