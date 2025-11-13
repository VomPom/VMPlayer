package com.vompom.media.docode.track

import android.util.Size
import android.view.Surface
import com.vompom.media.docode.decorder.IDecoder
import com.vompom.media.docode.decorder.VideoDecoder
import com.vompom.media.model.TrackSegment

/**
 *
 * Created by @juliswang on 2025/10/10 18:42
 *
 * @Description 负责视频轨道的管理，支持视频尺寸变化通知
 */

class VideoDecoderTrack() : BaseDecoderTrack() {
    private lateinit var outputSurface: Surface
    private var videoSizeChangeListener: ((Size) -> Unit)? = null

    constructor(segmentList: List<TrackSegment>, outputSurface: Surface, exportMode: Boolean = false) : this() {
        this.outputSurface = outputSurface
        this.exportMode = exportMode
        setTrackSegments(segmentList)
        decodeType = IDecoder.DecodeType.Video
    }

    /**
     * 设置视频尺寸变化监听器
     */
    fun setVideoSizeChangeListener(listener: (Size) -> Unit) {
        videoSizeChangeListener = listener
    }

    /**
     * 获取当前视频尺寸
     */
    fun getVideoSize(): Size {
        return (currentDecoder as? VideoDecoder)?.getVideoSize() ?: Size(0, 0)
    }

    override fun prepare() {
        nextSegment()
    }

    override fun createDecoder(segment: TrackSegment): IDecoder {
        val decoder = VideoDecoder(segment.asset, outputSurface)
        decoder.setExportMode(exportMode)
        decoder.start()

        // 设置视频尺寸变化监听，同时立即获取当前尺寸并通知
        decoder.setVideoSizeChangeListener { size ->
            videoSizeChangeListener?.invoke(size)
        }

        // 在解码器准备完成后，立即获取并通知视频尺寸
        val currentSize = decoder.getVideoSize()
        if (currentSize.width > 0 && currentSize.height > 0) {
            videoSizeChangeListener?.invoke(currentSize)
        }

        return decoder
    }

    /**
     * 获取当前解码器的视频尺寸
     */
    private fun getCurrentVideoSize(): Size {
        return (currentDecoder as? VideoDecoder)?.getVideoSize() ?: Size(0, 0)
    }

}