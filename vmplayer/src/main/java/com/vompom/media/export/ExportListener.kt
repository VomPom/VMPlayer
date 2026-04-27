package com.vompom.media.export

import java.io.File

/**
 *
 * Created by @juliswang on 2025/11/05 21:58
 *
 * @Description 导出监听器（抽离自 Exporter，方便跨模块使用）
 */
interface ExportListener {
    fun onExportStart()
    fun onExportProgress(progress: Float)
    fun onExportComplete(outputFile: File)
    fun onExportError(error: Exception)
}
