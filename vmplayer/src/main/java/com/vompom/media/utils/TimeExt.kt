package com.vompom.media.utils

/**
 *
 * Created by @juliswang on 2025/09/28 21:01
 *
 * @Description
 */

const val S_TO_MS = 1000
const val S_TO_US = 1000_000

const val MS_TO_US = 1000
const val US_TO_MS = 1000

fun sToMs(s: Float): Long {
    return (s * S_TO_MS).toLong()
}

fun sToMs(s: Long): Long {
    return s * S_TO_MS
}

fun sToUs(s: Float): Float {
    return s * S_TO_US
}

fun sToUs(s: Long): Long {
    return s * S_TO_US
}

fun msToS(ms: Long): Long {
    return ms / S_TO_MS
}

fun msToS(ms: Float): Float {
    return ms / S_TO_MS
}

fun usToS(us: Float): Float {
    return us / S_TO_US
}

fun usToS(us: Long): Long {
    return us / S_TO_US
}

fun usToMs(us: Long): Long {
    return us / MS_TO_US
}

fun msToUs(ms: Long): Long {
    return ms * MS_TO_US
}

/**
 * 将微秒格式化为时间字符串
 * 格式: "分:秒" 或 "时:分:秒"
 * @param us 微秒数
 * @return 格式化的时间字符串
 */
fun formatTimeFromUs(us: Long): String {
    val totalSeconds = usToS(us)
    return formatTimeFromSeconds(totalSeconds)
}

/**
 * 将秒数格式化为时间字符串
 * 格式: "分:秒" 或 "时:分:秒"
 * @param seconds 秒数
 * @return 格式化的时间字符串
 */
fun formatTimeFromSeconds(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        // 格式: "时:分:秒"
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        // 格式: "分:秒"
        String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * 将秒数格式化为时间字符串
 * 格式: "分:秒" 或 "时:分:秒"
 * @param seconds 秒数
 * @return 格式化的时间字符串
 */
fun formatTimeFromSeconds(seconds: Float): String {
    return formatTimeFromSeconds(seconds.toLong())
}

/**
 * 将微秒格式化为时间字符串
 * 格式: "分:秒" 或 "时:分:秒"
 * @param us 微秒数
 * @return 格式化的时间字符串
 */
fun formatTimeFromUs(us: Float): String {
    val totalSeconds = usToS(us)
    return formatTimeFromSeconds(totalSeconds.toLong())
}