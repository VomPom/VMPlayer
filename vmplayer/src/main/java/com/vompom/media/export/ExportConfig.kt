package com.vompom.media.export

import android.util.Size
import java.io.File

/**
 *
 * Created by @juliswang on 2025/11/05 21:58
 *
 * @Description 导出配置（抽离自 Exporter，方便跨模块使用）
 */
data class ExportConfig(
    val outputFile: File,
    val outputSize: Size = Size(1280, 720),
    val videoBitRate: Int = 2000000, // 2Mbps
    val audioSampleRate: Int = 44100,
    val audioBitRate: Int = 128000, // 128kbps
    val frameRate: Int = 30
)
