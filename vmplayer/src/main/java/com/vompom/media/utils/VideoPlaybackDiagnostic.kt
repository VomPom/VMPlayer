package com.vompom.media.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 视频播放诊断工具
 *
 * 用于分析合成的视频文件，识别可能导致无法播放的问题
 *
 * Created by @juliswang on 2025/11/06
 */
object VideoPlaybackDiagnostic {
    private const val TAG = "VideoPlaybackDiagnostic"

    data class DiagnosticResult(
        val canPlay: Boolean,
        val issues: List<String>,
        val videoInfo: VideoInfo?,
        val audioInfo: AudioInfo?
    )

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val bitRate: Int,
        val frameRate: Float,
        val duration: Long,
        val codec: String,
        val trackCount: Int
    )

    data class AudioInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val bitRate: Int,
        val duration: Long,
        val codec: String
    )

    /**
     * 诊断视频文件
     */
    fun diagnose(videoFile: File): DiagnosticResult {
        VLog.d(TAG, "Starting diagnostic for: ${videoFile.absolutePath}")

        val issues = mutableListOf<String>()
        var videoInfo: VideoInfo? = null
        var audioInfo: AudioInfo? = null

        // 1. 检查文件是否存在
        if (!videoFile.exists()) {
            issues.add("Video file does not exist")
            return DiagnosticResult(false, issues, null, null)
        }

        // 2. 检查文件大小
        val fileSize = videoFile.length()
        if (fileSize == 0L) {
            issues.add("Video file is empty (0 bytes)")
            return DiagnosticResult(false, issues, null, null)
        }
        VLog.d(TAG, "File size: $fileSize bytes")

        // 3. 使用MediaExtractor分析文件
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)

            val trackCount = extractor.trackCount
            VLog.d(TAG, "Track count: $trackCount")

            if (trackCount == 0) {
                issues.add("No tracks found in video file")
                return DiagnosticResult(false, issues, null, null)
            }

            var hasVideoTrack = false
            var hasAudioTrack = false

            // 分析每个track
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME) ?: "unknown"

                VLog.d(TAG, "Track $i: $mimeType")
                VLog.d(TAG, "Track $i format: $format")

                when {
                    mimeType.startsWith("video/") -> {
                        hasVideoTrack = true
                        videoInfo = extractVideoInfo(format, trackCount)
                        validateVideoTrack(format, issues)
                    }

                    mimeType.startsWith("audio/") -> {
                        hasAudioTrack = true
                        audioInfo = extractAudioInfo(format)
                        validateAudioTrack(format, issues)
                    }
                }
            }

            if (!hasVideoTrack) {
                issues.add("No video track found")
            }

            extractor.release()

        } catch (e: Exception) {
            issues.add("Failed to analyze file with MediaExtractor: ${e.message}")
            VLog.e(TAG, "MediaExtractor analysis failed", e)
        }

        // 4. 使用MediaMetadataRetriever获取更多信息
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0

            VLog.d(TAG, "Metadata - Duration: ${duration}ms, Resolution: ${width}x${height}, Bitrate: ${bitrate}bps")

            if (duration == 0L) {
                issues.add("Video duration is 0")
            }

            if (width == 0 || height == 0) {
                issues.add("Invalid video resolution: ${width}x${height}")
            }

            retriever.release()

        } catch (e: Exception) {
            issues.add("Failed to retrieve metadata: ${e.message}")
            VLog.e(TAG, "MediaMetadataRetriever failed", e)
        }

        // 5. 检查常见的编码问题
        checkCommonEncodingIssues(videoFile, issues)

        val canPlay = issues.isEmpty()

        VLog.d(TAG, "Diagnostic completed. Can play: $canPlay, Issues: ${issues.size}")
        issues.forEach { issue ->
            VLog.w(TAG, "Issue: $issue")
        }

        return DiagnosticResult(canPlay, issues, videoInfo, audioInfo)
    }

    private fun extractVideoInfo(format: MediaFormat, trackCount: Int): VideoInfo {
        return VideoInfo(
            width = format.getInteger(MediaFormat.KEY_WIDTH),
            height = format.getInteger(MediaFormat.KEY_HEIGHT),
            bitRate = format.getIntegerSafely(MediaFormat.KEY_BIT_RATE, 0),
            frameRate = format.getFloatSafely(MediaFormat.KEY_FRAME_RATE, 0f),
            duration = format.getLongSafely(MediaFormat.KEY_DURATION, 0L),
            codec = format.getString(MediaFormat.KEY_MIME) ?: "unknown",
            trackCount = trackCount
        )
    }

    private fun extractAudioInfo(format: MediaFormat): AudioInfo {
        return AudioInfo(
            sampleRate = format.getIntegerSafely(MediaFormat.KEY_SAMPLE_RATE, 0),
            channelCount = format.getIntegerSafely(MediaFormat.KEY_CHANNEL_COUNT, 0),
            bitRate = format.getIntegerSafely(MediaFormat.KEY_BIT_RATE, 0),
            duration = format.getLongSafely(MediaFormat.KEY_DURATION, 0L),
            codec = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
        )
    }

    private fun validateVideoTrack(format: MediaFormat, issues: MutableList<String>) {
        // 检查分辨率
        val width = format.getIntegerSafely(MediaFormat.KEY_WIDTH, 0)
        val height = format.getIntegerSafely(MediaFormat.KEY_HEIGHT, 0)
        if (width <= 0 || height <= 0) {
            issues.add("Invalid video resolution: ${width}x${height}")
        }

        // 检查帧率
        val frameRate = format.getFloatSafely(MediaFormat.KEY_FRAME_RATE, 0f)
        if (frameRate <= 0) {
            issues.add("Invalid frame rate: $frameRate")
        }

        // 检查编码格式
        val mimeType = format.getString(MediaFormat.KEY_MIME)
        if (mimeType != MediaFormat.MIMETYPE_VIDEO_AVC && mimeType != MediaFormat.MIMETYPE_VIDEO_HEVC) {
            issues.add("Unsupported video codec: $mimeType")
        }

        // 检查颜色格式
        val colorFormat = format.getIntegerSafely(MediaFormat.KEY_COLOR_FORMAT, -1)
        VLog.d(TAG, "Video color format: $colorFormat")
    }

    private fun validateAudioTrack(format: MediaFormat, issues: MutableList<String>) {
        // 检查采样率
        val sampleRate = format.getIntegerSafely(MediaFormat.KEY_SAMPLE_RATE, 0)
        if (sampleRate <= 0) {
            issues.add("Invalid audio sample rate: $sampleRate")
        }

        // 检查声道数
        val channelCount = format.getIntegerSafely(MediaFormat.KEY_CHANNEL_COUNT, 0)
        if (channelCount <= 0) {
            issues.add("Invalid audio channel count: $channelCount")
        }

        // 检查编码格式
        val mimeType = format.getString(MediaFormat.KEY_MIME)
        if (mimeType != MediaFormat.MIMETYPE_AUDIO_AAC) {
            issues.add("Unsupported audio codec: $mimeType")
        }
    }

    private fun checkCommonEncodingIssues(videoFile: File, issues: MutableList<String>) {
        // 检查文件扩展名
        if (!videoFile.name.endsWith(".mp4", ignoreCase = true)) {
            issues.add("File extension is not .mp4: ${videoFile.name}")
        }

        // 检查文件大小是否异常小
        val fileSize = videoFile.length()
        if (fileSize < 1024) { // 小于1KB
            issues.add("File size is suspiciously small: $fileSize bytes")
        }
    }

    // 安全地获取MediaFormat中的值，避免异常
    private fun MediaFormat.getIntegerSafely(key: String, defaultValue: Int): Int {
        return try {
            getInteger(key)
        } catch (e: Exception) {
            VLog.w(TAG, "Failed to get integer for key $key: ${e.message}")
            defaultValue
        }
    }

    private fun MediaFormat.getLongSafely(key: String, defaultValue: Long): Long {
        return try {
            getLong(key)
        } catch (e: Exception) {
            VLog.w(TAG, "Failed to get long for key $key: ${e.message}")
            defaultValue
        }
    }

    private fun MediaFormat.getFloatSafely(key: String, defaultValue: Float): Float {
        return try {
            getFloat(key)
        } catch (e: Exception) {
            VLog.w(TAG, "Failed to get float for key $key: ${e.message}")
            defaultValue
        }
    }

    /**
     * 打印诊断报告
     */
    fun printDiagnosticReport(result: DiagnosticResult) {
        VLog.i(TAG, "=== Video Playback Diagnostic Report ===")
        VLog.i(TAG, "Can Play: ${result.canPlay}")

        if (result.issues.isNotEmpty()) {
            VLog.i(TAG, "Issues Found:")
            result.issues.forEachIndexed { index, issue ->
                VLog.i(TAG, "  ${index + 1}. $issue")
            }
        }

        result.videoInfo?.let { video ->
            VLog.i(TAG, "Video Info:")
            VLog.i(TAG, "  Resolution: ${video.width}x${video.height}")
            VLog.i(TAG, "  Frame Rate: ${video.frameRate} fps")
            VLog.i(TAG, "  Bit Rate: ${video.bitRate} bps")
            VLog.i(TAG, "  Duration: ${video.duration} us")
            VLog.i(TAG, "  Codec: ${video.codec}")
        }

        result.audioInfo?.let { audio ->
            VLog.i(TAG, "Audio Info:")
            VLog.i(TAG, "  Sample Rate: ${audio.sampleRate} Hz")
            VLog.i(TAG, "  Channels: ${audio.channelCount}")
            VLog.i(TAG, "  Bit Rate: ${audio.bitRate} bps")
            VLog.i(TAG, "  Duration: ${audio.duration} us")
            VLog.i(TAG, "  Codec: ${audio.codec}")
        }

        VLog.i(TAG, "=== End of Report ===")
    }
}