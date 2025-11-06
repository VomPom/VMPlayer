package com.vompom.media.utils

import android.util.Log

/**
 *
 * Created by @juliswang on 2025/09/25 20:54
 *
 * @Description Video Player Logging Utility
 */

object VLog {
    private const val DEFAULT_TAG = "--vompom"

    enum class LogLevel(val priority: Int) {
        VERBOSE(Log.VERBOSE),
        DEBUG(Log.DEBUG),
        INFO(Log.INFO),
        WARN(Log.WARN),
        ERROR(Log.ERROR)
    }

    private var currentLevel: LogLevel = LogLevel.DEBUG
    private var isEnabled: Boolean = true

    /**
     * 设置日志级别
     */
    fun setLogLevel(level: LogLevel) {
        this.currentLevel = level
    }

    /**
     * 启用或禁用日志
     */
    fun setEnabled(enabled: Boolean) {
        this.isEnabled = enabled
    }

    /**
     * 检查是否应该输出指定级别的日志
     */
    private fun isLoggable(level: LogLevel): Boolean {
        return isEnabled && level.priority >= currentLevel.priority
    }

    fun v(msg: String) {
        v(DEFAULT_TAG, msg)
    }

    fun v(tag: String, msg: String) {
        if (isLoggable(LogLevel.VERBOSE)) {
            Log.v("tag", msg)
        }
    }

    fun d(msg: String) {
        d(DEFAULT_TAG, msg)
    }

    fun d(tag: String, msg: String) {
        if (isLoggable(LogLevel.DEBUG)) {
            Log.d(tag, msg)
        }
    }

    fun i(msg: String) {
        i(DEFAULT_TAG, msg)
    }

    fun i(tag: String, msg: String) {
        if (isLoggable(LogLevel.INFO)) {
            Log.i(tag, msg)
        }
    }

    fun w(msg: String) {
        w(DEFAULT_TAG, msg)
    }

    fun w(tag: String, msg: String) {
        if (isLoggable(LogLevel.WARN)) {
            Log.w(tag, msg)
        }
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        if (isLoggable(LogLevel.WARN)) {
            Log.w(tag, msg, tr)
        }
    }

    fun e(msg: String) {
        e(DEFAULT_TAG, msg)
    }

    fun e(tag: String, msg: String) {
        if (isLoggable(LogLevel.ERROR)) {
            Log.e(tag, msg)
        }
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        if (isLoggable(LogLevel.ERROR)) {
            Log.e(tag, msg, tr)
        }
    }

    /**
     * 输出播放器状态日志
     */
    fun player(msg: String) {
        d("Player", msg)
    }

    /**
     * 输出渲染相关日志
     */
    fun render(msg: String) {
        d("Render", msg)
    }

    /**
     * 输出解码相关日志
     */
    fun decoder(msg: String) {
        d("Decoder", msg)
    }

    /**
     * 输出导出相关日志
     */
    fun export(msg: String) {
        d("Export", msg)
    }

    /**
     * 输出音频相关日志
     */
    fun audio(msg: String) {
        d("Audio", msg)
    }

    /**
     * 输出视频相关日志
     */
    fun video(msg: String) {
        d("Video", msg)
    }
}