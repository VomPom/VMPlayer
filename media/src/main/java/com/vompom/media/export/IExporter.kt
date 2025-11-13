package com.vompom.media.export

import com.vompom.media.export.Exporter.ExportConfig
import com.vompom.media.export.Exporter.ExportListener
import java.io.File

/**
 *
 * Created by @juliswang on 2025/11/10 20:41
 *
 * @Description
 */

interface IExporter {
    fun export(outputFile: File?, config: ExportConfig , listener: ExportListener?)
    fun stopExport()
}