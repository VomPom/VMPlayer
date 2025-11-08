package com.vompom.media.export

import android.media.MediaCodec
import android.util.Size
import com.vompom.media.model.TrackSegment
import com.vompom.media.export.encoder.AudioEncoder
import com.vompom.media.export.encoder.IEncoder
import com.vompom.media.export.encoder.VideoEncoder
import com.vompom.media.export.reader.AudioReader
import com.vompom.media.export.reader.IReader
import com.vompom.media.export.reader.VideoReader
import com.vompom.media.utils.VLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 *
 * Created by @juliswang on 2025/11/05 21:58
 *
 * @Description 负责协调视频和音频写入 mp4 文件
 * todo:: 使用子线程去处理整个流程
 */

class ExportManager() {
    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var exportListener: ExportListener? = null

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

    // 配置数据
    private var segments: List<TrackSegment> = emptyList()
    private lateinit var config: ExportConfig

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
     * 设置导出监听器
     */
    fun setExportListener(listener: ExportListener?) {
        this.exportListener = listener
    }

    /**
     * 开始导出
     */
    fun startExport(
        segments: List<TrackSegment>,
        config: ExportConfig,
        listener: ExportListener
    ) {
        this.segments = segments
        this.config = config

        setExportListener(listener)
        initMediaMuxer()
        initEncoders()
        initDecoders()
        exportScope.launch {
            startTask()
        }
    }

    private fun initMediaMuxer() {
        config.outputFile.parentFile?.mkdirs()
        mediaMuxer = DefaultMediaMuxer(config.outputFile.absolutePath)
    }

    private fun initEncoders() {
        videoEncoder = VideoEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
        }
        audioEncoder = AudioEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
        }
    }

    private fun initDecoders() {
        val surface = (videoEncoder as VideoEncoder).getEncoderSurface()
        videoReader = VideoReader(segments, surface, config.frameRate)
        audioReader = AudioReader(segments)
    }

    private suspend fun startTask() = withContext(Dispatchers.IO) {
        // 视频数据流
        // VideoReader → Surface → VideoEncoder → BaseEncoder.listen() → writeSampleData()
        videoReader?.let {
            it.listen(
                onBufferRead = { bufferTime ->
                    videoEncodeTime = bufferTime
                    videoEncoder?.listen(
                        onFormatChange = {
                            (mediaMuxer?.addTrack(it) ?: -1).apply {
                                videoTrackAdd = true
                                tryStartMuxer()
                            }
                        },
                        onBufferEncode = { trackIndex, buffer, bufferInfo ->
                            writeSampleData(trackIndex, buffer, bufferInfo)
                        })

                    updateProgress()
                },
                onFinished = {
                    videoEncoder?.finishEncoding()
                    videoFinished = true
                    tryStopMuxer()
                }
            )
            it.start()
        }

        // 音频数据流
        // AudioReader → setOnAudioDataAvailable → AudioEncoder.encodeAudioData() → BaseEncoder.listen() → writeSampleData()
        audioReader?.let {
            // 设置音频数据处理回调
            (it as AudioReader).setOnAudioDataAvailable { audioData, presentationTimeUs ->
                (audioEncoder as? AudioEncoder)?.encodeAudioData(audioData, presentationTimeUs)
            }

            it.listen(
                onBufferRead = { bufferTime ->
                    audioEncodeTime = bufferTime
                    // 音频编码在setOnAudioDataAvailable回调中已经处理
                    // 这里只需要处理格式变化和输出drain
                    audioEncoder?.listen(
                        onFormatChange = {
                            (mediaMuxer?.addTrack(it) ?: -1).apply {
                                audioTrackAdd = true
                                tryStartMuxer()
                            }
                        },
                        onBufferEncode = { trackIndex, buffer, bufferInfo ->
                            writeSampleData(trackIndex, buffer, bufferInfo)
                        })

                    updateProgress()
                },
                onFinished = {
                    audioEncoder?.finishEncoding()
                    audioFinished = true
                    tryStopMuxer()
                }
            )
            it.start()
        }
    }

    private fun tryStartMuxer() {
        synchronized(muxerLock) {
            if (videoTrackAdd && audioTrackAdd) {
                mediaMuxer?.start()
                muxerStart = true
                muxerLock.notify()
            }
        }
    }

    private fun tryStopMuxer() {
        if (videoFinished && audioFinished) {
            mediaMuxer?.stop()
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
        exportListener?.onExportProgress(progress)
    }

    private fun durationUs(): Long {
        if (totalVideoAudioTime < 0) {
            totalVideoAudioTime = 2 * segments.sumOf { it.timelineRange.durationUs }

        }
        return totalVideoAudioTime
    }

    /**
     * 根据输入片段推荐比特率
     */
    fun getRecommendedBitRate(outputSize: Size): Int {
        // 根据输出尺寸推荐合适的比特率
        val pixels = outputSize.width * outputSize.height
        return when {
            pixels <= 640 * 480 -> 1000000      // 1Mbps for SD
            pixels <= 1280 * 720 -> 2000000     // 2Mbps for HD
            pixels <= 1920 * 1080 -> 5000000    // 5Mbps for FHD
            else -> 8000000                      // 8Mbps for higher resolutions
        }
    }

    /**
     * 获取推荐的输出文件名
     */
    fun getRecommendedFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "export_$timestamp.mp4"
    }

    fun stopExport() {
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