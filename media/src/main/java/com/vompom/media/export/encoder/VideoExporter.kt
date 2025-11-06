package com.vompom.media.export.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.util.Size
import android.view.Surface
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.docode.model.TrackSegment
import com.vompom.media.docode.track.VideoDecoderTrack
import com.vompom.media.utils.VLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * 视频导出器
 *
 * 负责将多个视频片段合成并导出为MP4文件
 */
class VideoExporter(
    private val segments: List<TrackSegment>,
    private val outputFile: File,
    private val outputSize: Size = Size(1280, 720),
    private val videoBitRate: Int = 2000000, // 2Mbps
    private val audioSampleRate: Int = 44100,
    private val audioBitRate: Int = 128000, // 128kbps
    private val frameRate: Int = 30
) {
    companion object {
        private const val TAG = "VideoExporter"
        private const val TIMEOUT_US = 10000L
        private const val VIDEO_MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val AUDIO_MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
    }

    private var mediaMuxer: MediaMuxer? = null
    private var videoEncoder: MediaCodec? = null
    private var audioEncoder: MediaCodec? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var videoEncodeTime = 0L
    private var audioEncodeTime = 0L
    private var totalVideoAudioTime = 0L
    private var isExporting = false
    private var exportListener: ExportListener? = null

    // 添加muxer状态跟踪
    private var isMuxerStarted = false
    private var isMuxerStopped = false

    // 解码轨道
    private var videoDecoderTrack: VideoDecoderTrack? = null
    private var exportAudioTrack: ExportAudioDecoderTrack? = null

    // 编码器输入Surface
    private var encoderInputSurface: Surface? = null

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
    suspend fun startExport() = withContext(Dispatchers.IO) {
        videoEncodeTime = 0
        audioEncodeTime = 0
        // 重置状态标志
        isMuxerStarted = false
        isMuxerStopped = false

        if (isExporting) {
            Log.w(TAG, "Export already in progress")
            return@withContext
        }

        try {
            isExporting = true
            exportListener?.onExportStart()

            setupMuxer()
            setupEncoders()
            setupDecoderTracks()
            processSegments()

            exportListener?.onExportComplete(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            exportListener?.onExportError(e)
        } finally {
            cleanup()
            isExporting = false
        }
    }

    /**
     * 停止导出
     */
    fun stopExport() {
        isExporting = false
        outputFile.delete()
    }

    private fun setupMuxer() {
        outputFile.parentFile?.mkdirs()
        mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    private fun setupEncoders() {
        setupVideoEncoder()
        setupAudioEncoder()
    }

    private fun setupVideoEncoder() {
        val format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, outputSize.width, outputSize.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoderInputSurface = createInputSurface()
        }

        VLog.d(TAG, "Video encoder configured: ${outputSize.width}x${outputSize.height}, ${videoBitRate}bps")
    }

    private fun setupAudioEncoder() {
        val format = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, audioSampleRate, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, audioBitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        VLog.d(TAG, "Audio encoder configured: ${audioSampleRate}Hz, ${audioBitRate}bps")
    }

    private fun setupDecoderTracks() {
        // 创建视频解码轨道，输出到编码器的输入Surface
        encoderInputSurface?.let { surface ->
            videoDecoderTrack = VideoDecoderTrack(segments, surface).apply {
                prepare()
            }
        }

        // 创建音频解码轨道（需要修改AudioDecoderTrack以支持数据输出而不是直接播放）
        exportAudioTrack = ExportAudioDecoderTrack(segments).apply {
            prepare()
        }
    }

    private suspend fun processSegments() {
        val totalDurationUs = segments.sumOf { it.timelineRange.durationUs }
        totalVideoAudioTime = totalDurationUs * 2L
        // 启动编码器
        videoEncoder?.start()
        audioEncoder?.start()

        // 开始音视频数据处理
        coroutineScope {
            val videoJob = async { processVideoTrack(totalDurationUs) }
            val audioJob = async { processAudioTrack(totalDurationUs) }  // 启用音频处理

            // 等待音视频处理完成
            videoJob.await()
            audioJob.await()  // 等待音频处理完成
        }

        // 结束编码
        finishEncoding()
    }


    private fun processVideoTrack(totalDurationUs: Long) {
        Log.d(TAG, "Starting video track processing")
        val frameDurationUs = 1000000L / frameRate // 每帧的时长

        while (videoEncodeTime < totalDurationUs && isExporting && !isMuxerStopped) {
            try {
                // 从视频轨道读取帧
                val sampleState = videoDecoderTrack?.readSample(videoEncodeTime)
                VLog.d("[processVideoTrack] timeline:${videoEncodeTime} state: ${sampleState?.toString()}")
                if (sampleState?.stateCode == IDecoder.SAMPLE_STATE_FINISH) {
                    Log.d(TAG, "Video track finished")
                    break
                }

                // 处理编码器输出
                drainEncoder(videoEncoder, videoTrackIndex)

                videoEncodeTime += frameDurationUs

                updateProgress()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing video frame at ${videoEncodeTime}us", e)
                break
            }
        }

        Log.d(TAG, "Video track processing completed")
    }

    private fun updateProgress() {
        var progress = (audioEncodeTime + videoEncodeTime) / totalVideoAudioTime.toFloat()
        if (progress > 1) {
            progress = 1f
        }
        exportListener?.onExportProgress(progress)
    }

    private fun processAudioTrack(totalDurationUs: Long) {
        Log.d(TAG, "Starting audio track processing")
        val sampleDurationUs = 23220L // 约23ms per sample for 44.1kHz

        while (audioEncodeTime < totalDurationUs && isExporting && !isMuxerStopped) {
            try {
                // 从音频轨道读取样本
                val sampleState = exportAudioTrack?.readSample(audioEncodeTime)
                VLog.d("[processAudioTrack] timeline:${audioEncodeTime} state: ${sampleState?.toString()}")

                if (sampleState?.stateCode == IDecoder.SAMPLE_STATE_FINISH) {
                    Log.d(TAG, "Audio track finished")
                    break
                }

                // 如果音频轨道支持获取原始PCM数据，则将数据送入音频编码器
                exportAudioTrack?.let { exportTrack ->
                    val audioData = exportTrack.getLastDecodedData()
                    val bufferInfo = exportTrack.getLastBufferInfo()

                    if (audioData != null && bufferInfo != null) {
                        // 使用解码时的真实时间戳而不是人工计算的
                        encodeAudioData(audioData, audioEncodeTime)
                        VLog.d("[processAudioTrack] encoded audio data size: ${audioData.remaining()}, pts: $audioEncodeTime")
                    }
                }

                // 处理编码器输出 - 这里要持续drain，不能只调用一次
                var outputAvailable = true
                while (outputAvailable && !isMuxerStopped) {
                    outputAvailable = drainAudioEncoder()
                }

                audioEncodeTime += sampleDurationUs
                updateProgress()

            } catch (e: Exception) {
                Log.e(TAG, "Error processing audio sample at ${audioEncodeTime}us", e)
                break
            }
        }

        // 发送音频EOS - 移除这里的EOS发送，统一在finishEncoding中处理
        // sendAudioEOS()

        // 最后再drain一遍确保所有数据都被处理
        while (drainAudioEncoder() && !isMuxerStopped) {
            // 持续drain直到没有更多输出
        }

        Log.d(TAG, "Audio track processing completed")
    }

    private fun encodeAudioData(audioData: ByteBuffer, presentationTimeUs: Long) {
        val encoder = audioEncoder ?: return

        // 确保数据处于可读状态
        if (audioData.remaining() <= 0) {
            VLog.w("[encodeAudioData] Empty audio data, skip")
            return
        }

        try {
            val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                inputBuffer?.let { buffer ->
                    buffer.clear()

                    val dataSize = audioData.remaining()
                    val bufferCapacity = buffer.capacity()

                    VLog.d("[encodeAudioData] Input data size: $dataSize, buffer capacity: $bufferCapacity")

                    if (dataSize <= bufferCapacity) {
                        // 数据可以完全放入buffer
                        buffer.put(audioData)

                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            dataSize,
                            presentationTimeUs,
                            0
                        )
                        VLog.d("[encodeAudioData] Successfully queued audio data: size=$dataSize, pts=$presentationTimeUs")
                    } else {
                        // 数据太大，需要分块处理
                        VLog.w("[encodeAudioData] Audio data too large ($dataSize > $bufferCapacity), truncating")
                        val originalLimit = audioData.limit()
                        audioData.limit(audioData.position() + bufferCapacity)
                        buffer.put(audioData)
                        audioData.limit(originalLimit) // 恢复原limit

                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            bufferCapacity,
                            presentationTimeUs,
                            0
                        )
                    }
                }
            } else {
                VLog.w("[encodeAudioData] No input buffer available (index: $inputBufferIndex)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding audio data", e)
        }
    }

    // 新增：持续drain音频编码器直到没有更多输出
    private fun drainAudioEncoder(): Boolean {
        val encoder = audioEncoder ?: return false
        val bufferInfo = MediaCodec.BufferInfo()
        val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

        when {
            outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // 没有更多数据
                return false
            }

            outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = encoder.outputFormat
                Log.d(TAG, "Audio encoder output format changed: $newFormat")
                if (audioTrackIndex == -1) {
                    audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                    Log.d(TAG, "Audio track added, index: $audioTrackIndex")
                }
                if (!isMuxerStarted && videoTrackIndex != -1) {
                    // 如果有视频轨道就可以启动，音频轨道是可选的
                    mediaMuxer?.start()
                    isMuxerStarted = true
                    isMuxerStopped = false
                    Log.d(TAG, "MediaMuxer started with video track: $videoTrackIndex, audio track: $audioTrackIndex")
                }
                return true
            }

            outputBufferIndex >= 0 -> {
                val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                outputBuffer?.let { buffer ->
                    if (bufferInfo.size > 0 && isMuxerStarted && !isMuxerStopped) {
                        if (audioTrackIndex != -1) {
                            try {
                                mediaMuxer?.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                                VLog.d("[drainAudioEncoder] writeSampleData:${bufferInfo.presentationTimeUs}")
                            } catch (e: IllegalStateException) {
                                Log.e(TAG, "MediaMuxer may have stopped before writing audio sample data: ${e.message}")
                                isMuxerStopped = true
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(outputBufferIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    return false
                }
                return true
            }
        }
        return false
    }

    // 新增：发送音频EOS
    private fun sendAudioEOS() {
        audioEncoder?.let { encoder ->
            try {
                val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputBufferIndex, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    Log.d(TAG, "Audio EOS signal sent")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audio EOS", e)
            }
        }
    }


    private fun finishEncoding() {
        Log.d(TAG, "Starting finishEncoding...")

        // 如果MediaMuxer已经停止，跳过EOS处理
        if (isMuxerStopped) {
            Log.d(TAG, "MediaMuxer already stopped, skipping EOS处理")
            return
        }

        // 发送视频EOS信号
        videoEncoder?.let { encoder ->
            try {
                encoder.signalEndOfInputStream()
                Log.d(TAG, "Video EOS signal sent")

                // 继续drain视频编码器直到EOS
                drainVideoEncoderUntilEOS(encoder)
            } catch (e: Exception) {
                Log.e(TAG, "Error in video EOS processing", e)
            }
        }

        // 发送音频EOS信号
        audioEncoder?.let { encoder ->
            try {
                val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputBufferIndex, 0, 0, 0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    Log.d(TAG, "Audio EOS signal sent")
                }

                // 继续drain音频编码器直到EOS
                drainAudioEncoderUntilEOS(encoder)
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio EOS processing", e)
            }
        }

        Log.d(TAG, "finishEncoding completed")
    }

    private fun drainVideoEncoderUntilEOS(encoder: MediaCodec) {
        var eosReceived = false
        while (!eosReceived && isMuxerStarted && !isMuxerStopped) {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    continue
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let { buffer ->
                        if (bufferInfo.size > 0 && videoTrackIndex != -1) {
                            try {
                                mediaMuxer?.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                            } catch (e: IllegalStateException) {
                                Log.w(TAG, "MediaMuxer stopped during video EOS processing: ${e.message}")
                                isMuxerStopped = true
                                eosReceived = true
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReceived = true
                        Log.d(TAG, "Video EOS received")
                    }
                }
            }
        }
    }

    private fun drainAudioEncoderUntilEOS(encoder: MediaCodec) {
        var eosReceived = false
        while (!eosReceived && isMuxerStarted && !isMuxerStopped) {
            val bufferInfo = MediaCodec.BufferInfo()
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    continue
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let { buffer ->
                        if (bufferInfo.size > 0 && audioTrackIndex != -1) {
                            try {
                                mediaMuxer?.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                            } catch (e: IllegalStateException) {
                                Log.w(TAG, "MediaMuxer stopped during audio EOS processing: ${e.message}")
                                isMuxerStopped = true
                                eosReceived = true
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReceived = true
                        Log.d(TAG, "Audio EOS received")
                    }
                }
            }
        }
    }

    private fun drainEncoder(encoder: MediaCodec?, trackIndex: Int) {
        encoder ?: return

        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            VLog.d("--julis outputBufferIndex: $outputBufferIndex ${encoder.hashCode()}")
            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break // 没有更多数据
                }

                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 编码器输出格式变化，添加轨道到muxer
                    val newFormat = encoder.outputFormat
                    Log.d(TAG, "Encoder output format changed: $newFormat")

                    // 根据编码器类型添加轨道
                    when (encoder) {
                        videoEncoder -> {
                            if (videoTrackIndex == -1) {
                                videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Video track added, index: $videoTrackIndex")
                            }
                        }

                        audioEncoder -> {
                            if (audioTrackIndex == -1) {
                                audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                Log.d(TAG, "Audio track added, index: $audioTrackIndex")
                            }
                        }
                    }

                    // 检查是否可以启动muxer - 优先支持视频轨道，音频轨道可选
                    if (!isMuxerStarted && videoTrackIndex != -1) {
                        // 如果有视频轨道就可以启动，音频轨道是可选的
                        mediaMuxer?.start()
                        isMuxerStarted = true
                        isMuxerStopped = false
                        Log.d(
                            TAG,
                            "MediaMuxer started with video track: $videoTrackIndex, audio track: $audioTrackIndex"
                        )
                    }
                }

                outputBufferIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let { buffer ->
                        // 只有在muxer启动后才写入数据
                        if (bufferInfo.size > 0 && isMuxerStarted && !isMuxerStopped) {
                            val currentTrackIndex = when (encoder) {
                                videoEncoder -> videoTrackIndex
                                audioEncoder -> audioTrackIndex
                                else -> -1
                            }
                            if (currentTrackIndex != -1) {
                                try {
                                    mediaMuxer?.writeSampleData(currentTrackIndex, buffer, bufferInfo)
                                    VLog.d("--julis writeSampleData:${bufferInfo.presentationTimeUs}")
                                } catch (e: IllegalStateException) {
                                    Log.e(TAG, "MediaMuxer may have stopped before writing sample data: ${e.message}")
                                    isMuxerStopped = true
                                }
                            }
                        }
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break // 编码结束
                    }
                }
            }
        }
    }

    private fun cleanup() {
        try {
            videoDecoderTrack?.release()
            exportAudioTrack?.release()
            exportAudioTrack = null

            // 安全停止和释放视频编码器
            videoEncoder?.let { encoder ->
                try {
                    encoder.stop()
                    Log.d(TAG, "Video encoder stopped successfully")
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "Video encoder already stopped: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping video encoder", e)
                } finally {
                    try {
                        encoder.release()
                        Log.d(TAG, "Video encoder released successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing video encoder", e)
                    }
                }
            }
            videoEncoder = null

            // 安全停止和释放音频编码器
            audioEncoder?.let { encoder ->
                try {
                    encoder.stop()
                    Log.d(TAG, "Audio encoder stopped successfully")
                } catch (e: IllegalStateException) {
                    Log.d(TAG, "Audio encoder already stopped: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping audio encoder", e)
                } finally {
                    try {
                        encoder.release()
                        Log.d(TAG, "Audio encoder released successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error releasing audio encoder", e)
                    }
                }
            }
            audioEncoder = null

            encoderInputSurface?.release()
            encoderInputSurface = null

            // 小心处理MediaMuxer的释放
            mediaMuxer?.let { muxer ->
                try {
                    // MediaMuxer.release() 内部会处理stop()，我们不需要手动调用stop()
                    muxer.release()
                    isMuxerStopped = true
                    Log.d(TAG, "MediaMuxer released successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaMuxer", e)
                }
            }
            mediaMuxer = null

            // Reset muxer state variables
            videoTrackIndex = -1
            audioTrackIndex = -1
            isMuxerStarted = false
            isMuxerStopped = false

            Log.d(TAG, "Cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

