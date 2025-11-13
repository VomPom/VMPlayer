package com.vompom.media.export

import android.media.MediaCodec
import android.util.Size
import android.view.Surface
import com.vompom.media.export.encoder.AudioEncoder
import com.vompom.media.export.encoder.IEncoder
import com.vompom.media.export.encoder.VideoEncoder
import com.vompom.media.export.reader.AudioReader
import com.vompom.media.export.reader.IReader
import com.vompom.media.export.reader.VideoReader
import com.vompom.media.model.TrackSegment
import com.vompom.media.utils.ThreadUtils
import com.vompom.media.utils.VLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/05 21:58
 *
 * @Description 负责协调视频和音频写入 mp4 文件
 * todo:: 使用子线程去处理整个流程
 * todo:: 使用一个 session 记录渲染的数据
 */

class Exporter(val segments: List<TrackSegment>) : IExporter {
    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listener: ExportListener? = null

    // 合成器
    private var mediaMuxer: IMediaMuxer? = null

    // 编码器
    private var videoEncoder: IEncoder? = null
    private var audioEncoder: IEncoder? = null

    // 解码器
    private var videoReader: IReader? = null
    private var audioReader: IReader? = null


    // 时间、状态记录等
    private var audioEncodeTime: Long = 0L
    private var videoEncodeTime: Long = 0L
    private var totalVideoAudioTime: Long = -1L

    private var videoFinished = false
    private var videoTrackAdd = false

    private var audioFinished = false
    private var audioTrackAdd = false

    private var muxerStart = false
    private var muxerLock = Object()

    private lateinit var config: ExportConfig


    override fun export(outputFile: File?, config: ExportConfig, listener: ExportListener?) {
        if (segments.isEmpty()) {
            listener?.onExportError(IllegalStateException("No segments to export"))
            return
        }
        this.config = config
        this.listener = object : ExportListener {
            override fun onExportStart() {
                runOnUiThread {
                    listener?.onExportStart()
                }
            }

            override fun onExportProgress(progress: Float) {
                runOnUiThread {
                    listener?.onExportProgress(progress)
                }
            }

            override fun onExportComplete(outputFile: File) {
                runOnUiThread {
                    listener?.onExportComplete(outputFile)
                }
            }

            override fun onExportError(error: Exception) {
                runOnUiThread {
                    listener?.onExportError(error)
                }
            }
        }
        startExport()
    }

    private fun runOnUiThread(block: () -> Unit) = ThreadUtils.runInMainThread { block() }

    /**
     * 开始导出
     */
    private fun startExport() {
        initEncoders()
        initDecoders()
        initMediaMuxer()
        exportScope.launch {
            readAndWrite(videoReader, videoEncoder, true)
            readAndWrite(audioReader, audioEncoder, false)
        }
    }

    private fun initMediaMuxer() {
        config.outputFile.parentFile?.mkdirs()
        mediaMuxer = DefaultMediaMuxer(config.outputFile.absolutePath)
    }

    private fun initEncoders(): Surface {
        AudioEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
            audioEncoder = this
        }
        return VideoEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
            videoEncoder = this
        }.getEncoderSurface()
    }

    private fun initDecoders() {
        val surface = (videoEncoder as VideoEncoder).getEncoderSurface()
        videoReader = VideoReader(segments, surface, config.frameRate)
        audioReader = AudioReader(segments)
    }


    /**
     * Read and write
     *  视频数据流
     *      VideoReader → Surface → VideoEncoder → BaseEncoder.listen() → writeSampleData()
     *  音频数据流
     *      AudioReader → setOnAudioDataAvailable → AudioEncoder.encodeAudioData() → BaseEncoder.listen() → writeSampleData()
     *
     * @param reader    音视频帧读取解码器
     * @param encoder   音视频编码器
     * @param isVideo   true 视频处理 false 音频处理
     */
    private fun readAndWrite(reader: IReader?, encoder: IEncoder?, isVideo: Boolean) {
        reader?.let {
            // 设置音频数据处理回调
            if (it is AudioReader) {
                it.setOnAudioDataAvailable { audioData, presentationTimeUs ->
                    (encoder as? AudioEncoder)?.encodeAudioData(audioData, presentationTimeUs)
                }
            }

            it.listen(
                onBufferRead = { bufferTime ->
                    if (isVideo) videoEncodeTime = bufferTime else audioEncodeTime = bufferTime
                    // 音频编码在 setOnAudioDataAvailable 回调中已经处理
                    // 这里只需要处理格式变化和输出 drain
                    encoder?.listen(
                        onFormatChange = {
                            (mediaMuxer?.addTrack(it) ?: -1).apply {
                                if (isVideo) videoTrackAdd = true else audioTrackAdd = true
                                tryStartMuxer()
                            }
                        },
                        onBufferEncode = { trackIndex, buffer, bufferInfo ->
                            writeSampleData(trackIndex, buffer, bufferInfo)
                        })

                    updateProgress()
                },
                onFinished = {
                    encoder?.finishEncoding()
                    if (isVideo) videoFinished = true else audioFinished = true
                    tryStopMuxer()
                }
            )
            it.start()
        }
    }

    /**
     * 启动 Muxer 需要视频和音频轨道都添加成功才能启动，否则进行等待
     *
     */
    private fun tryStartMuxer() {
        synchronized(muxerLock) {
            if (videoTrackAdd && audioTrackAdd) {
                mediaMuxer?.start()
                muxerStart = true
                muxerLock.notify()
                listener?.onExportStart()
            }
        }
    }

    private fun tryStopMuxer() {
        if (videoFinished && audioFinished) {
            mediaMuxer?.stop()
            listener?.onExportComplete(config.outputFile)
        }
    }

    private fun writeSampleData(trackIndex: Int, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        // 必须等待 Muxer 开始之后才能写入数据
        if (!muxerStart) {
            synchronized(muxerLock) {
                if (!muxerStart) {
                    try {
                        muxerLock.wait()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }
        }
        if (trackIndex >= 0) {
            mediaMuxer?.writeSampleData(trackIndex, byteBuf, bufferInfo)
        } else {
            VLog.d("trackIndex is invalid: $trackIndex, disable to write sample data.")
        }
    }

    private fun updateProgress() {
        var progress = (audioEncodeTime + videoEncodeTime) / durationUs().toFloat()
        if (progress > 1) {
            progress = 1f
        }
        listener?.onExportProgress(progress)
    }

    private fun durationUs(): Long {
        if (totalVideoAudioTime < 0) {
            totalVideoAudioTime = 2 * segments.sumOf { it.timelineRange.durationUs }

        }
        return totalVideoAudioTime
    }

    data class ExportConfig(
        val outputFile: File,
        val outputSize: Size = Size(1280, 720),
        val videoBitRate: Int = 2000000, // 2Mbps
        val audioSampleRate: Int = 44100,
        val audioBitRate: Int = 128000, // 128kbps
        val frameRate: Int = 30
    )

    /**
     * 导出监听器
     */
    interface ExportListener {
        fun onExportStart()
        fun onExportProgress(progress: Float)
        fun onExportComplete(outputFile: File)
        fun onExportError(error: Exception)
    }

    /**
     * 停止导出
     */
    override fun stopExport() {
        release()
    }

    /**
     * 释放资源
     */
    fun release() {
        videoReader?.stop()
        audioReader?.stop()

        audioEncoder?.release()
        videoEncoder?.release()
        mediaMuxer?.release()

        exportScope.cancel()
    }
}