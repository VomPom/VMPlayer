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
import com.vompom.media.model.RenderModel
import com.vompom.media.model.TrackSegment
import com.vompom.media.render.GLThread
import com.vompom.media.render.PlayerRender
import com.vompom.media.render.effect.EffectGroup
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
 */
class Exporter(val segments: List<TrackSegment>, val renderModel: RenderModel) : IExporter {
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
    override fun export(outputFile: File?, config: ExportConfig, listener: Exporter.ExportListener?) {
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
        initDecodersAndRender()
        initMediaMuxer()
    }

    private fun initMediaMuxer() {
        config.outputFile.parentFile?.mkdirs()
        mediaMuxer = DefaultMediaMuxer(config.outputFile.absolutePath)
    }

    private fun initEncoders() {
        AudioEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
            audioEncoder = this
        }
        VideoEncoder(config).apply {
            prepare()
            setBufferEncodeCallback { trackIndex, buffer, bufferInfo ->
                writeSampleData(trackIndex, buffer, bufferInfo)
            }
            videoEncoder = this
        }
    }

    /**
     * 初始化解码链路
     *
     * 1. 从 VideoEncoder 中获取编码输入的 Surface（Encoder 内部通过 createInputSurface 创建）
     * 2. 创建带特效的 PlayerRender（内部会创建用于解码输出的 SurfaceTexture/Surface）
     * 3. 启动 GL 渲染线程 GLThread，将编码器 Surface 作为最终渲染目标
     */
    private fun initDecodersAndRender() {
        // 编码器输入 Surface：VideoEncoder 使用 COLOR_FormatSurface 时通过 createInputSurface() 创建
        val encoderSurface = (videoEncoder as VideoEncoder).getEncoderSurface()
        // 创建负责处理 OES 纹理 + 特效链路的渲染器
        val renderer = initRender()
        // 启动 GLThread，将编码器 Surface 绑定为 EGL 的输出窗口
        initRender(renderer, encoderSurface)
    }

    /**
     * 绑定渲染器与输出 Surface，并启动 GL 渲染线程
     *
     * @param renderer 负责处理解码输出纹理和特效的渲染器
     * @param surface  作为 EGLWindowSurface 的目标 Surface，导出时通常为编码器的输入 Surface
     */
    fun initRender(renderer: PlayerRender, surface: Surface) {
        val glThread = GLThread(
            renderer,
            surface,            // 导出场景下，将编码器输入 Surface 作为 EGL 输出窗口
            null,               // 导出场景下不需要预览 View，仅使用编码 Surface
            config.outputSize
        )
        glThread.start()

    }

    /**
     * 构建用于导出流程的渲染器
     *
     * - 设置渲染尺寸，与导出视频分辨率保持一致
     * - 配置 Surface 回调：当内部创建用于解码输出的 Surface 后，触发 onRenderSurfaceCreate
     */
    private fun initRender(): PlayerRender {
        return PlayerRender().apply {
            setEffectGroup(
                EffectGroup.createEffectGroup(
                    renderModel.effectList
                )
            )
            initRenderSize(config.outputSize)

            // 当内部通过 OES 纹理创建出用于解码输出的 Surface 后回调
            // 这里拿到的 Surface 会传给 VideoReader 作为 MediaCodec Decoder 的输出目标
            setSurfaceReadyCallback { surface ->
                onRenderSurfaceCreate(surface)
            }
        }
    }

    /**
     * 当 GL 渲染线程创建用于解码输出的 Surface 时回调，这时候才能开始读取数据
     * @param surface 用于特效渲染的 Surface，注意与 Encoder 的 getEncoderSurface() 区分
     */
    private fun onRenderSurfaceCreate(surface: Surface) {
        videoReader = VideoReader(segments, surface, config.frameRate)
        audioReader = AudioReader(segments)
        exportScope.launch {
            readAndWrite(videoReader, videoEncoder, true)
            readAndWrite(audioReader, audioEncoder, false)
        }
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