package com.vompom.media

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.docode.decorder.VideoDecoder
import com.vompom.media.docode.model.ClipAsset
import com.vompom.media.docode.model.TrackSegment
import com.vompom.media.docode.track.AudioDecoderTrack
import com.vompom.media.docode.track.VideoDecoderTrack
import com.vompom.media.export.EncodeManager
import com.vompom.media.export.EncodeManager.ExportConfig
import com.vompom.media.export.EncodeManager.ExportListener
import com.vompom.media.player.PlayerThread
import com.vompom.media.render.VideoRenderView
import java.io.File

/**
 *
 * Created by @juliswang on 2025/09/25 20:42
 *
 * @Description 基于 [VideoDecoder] [AudioDecoder] 包装播放器，协调整个播放流程，管理播放状态
 *
 */
class VMPlayer : IPlayer, Handler.Callback {
    private var segments: List<TrackSegment> = emptyList()
    private var durationUs: Long = -1L

    private var playerThread: PlayerThread? = null
    private var playListener: IPlayer.PlayerListener? = null
    var mMainHandler: Handler = Handler(Looper.getMainLooper(), this)

    private var loop = true
    private var renderSize = Size(1280, 720)  // 固定渲染尺寸
    private var playUs: Long = 0L
    private var videoRenderView: VideoRenderView? = null

    // 导出相关
    private val exportManager = EncodeManager()
    private var exportListener: ExportListener? = null


    companion object {
        const val TYPE_STATES: Int = 1
        const val TYPE_PROGRESS: Int = 2
        const val TYPE_VIEWPORT_UPDATE: Int = 3

        fun create(frameLayout: FrameLayout): VMPlayer {
            return VMPlayer(frameLayout)
        }
    }

    private constructor(playerContainer: FrameLayout) {
        initContentView(playerContainer)
    }

    private fun initContentView(playerContainer: FrameLayout) {
        videoRenderView = VideoRenderView(playerContainer.context).apply {
            // 设置目标渲染尺寸
            setTargetRenderSize(renderSize)

            // 设置Surface准备完成的回调
            setSurfaceReadyCallback { surface ->
                onSurfaceCreate(surface)
            }

//            setEffectProcessor(SimpleVideoEffectProcessor())

            playerContainer.addView(
                this, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun onSurfaceCreate(surface: Surface) {
        val videoTrack = VideoDecoderTrack(segments, surface)
        val audioTrack = AudioDecoderTrack(segments)
        videoTrack.setVideoSizeChangeListener { videoSize ->
            videoRenderView?.updateVideoSize(videoSize)
        }

        playerThread = PlayerThread(
            this,
            videoTrack,
            audioTrack
        )
        playerThread?.sendMessage(PlayerThread.Companion.ACTION_PREPARE)
        play()
    }

    override fun setPlayList(assets: List<ClipAsset>) {
        segments = createTrackSegments(assets)
    }

    private fun createTrackSegments(assets: List<ClipAsset>): List<TrackSegment> {
        var preDurationUs = 0L
        val trackSegmentList = assets.map {
            val segment = TrackSegment(it)
            segment.timelineRange.updateStartUs(preDurationUs)
            preDurationUs += segment.timelineRange.durationUs
            segment
        }
        return trackSegmentList
    }

    override fun play() {
        playerThread?.sendMessage(PlayerThread.Companion.ACTION_PLAY)
    }

    override fun pause() {
        playerThread?.sendMessage(PlayerThread.Companion.ACTION_PAUSE)
    }

    override fun seekTo(positionUs: Long) {
        playerThread?.sendMessage(PlayerThread.Companion.ACTION_SEEK, positionUs)
    }

    override fun stop() {
        playerThread?.sendMessage(PlayerThread.Companion.ACTION_STOP)
    }

    /**
     * 导出视频到指定文件
     *
     * @param outputFile 输出文件，为null时使用默认文件名
     * @param config 导出配置，为null时使用默认配置
     */
    override fun export(outputFile: File?, config: ExportConfig?, listener: ExportListener?) {
        this.exportListener = listener
        if (segments.isEmpty()) {
            exportListener?.onExportError(IllegalStateException("No segments to export"))
            return
        }

        // 使用默认输出文件
        val finalOutputFile = outputFile ?: getDefaultExportFile()

        // 使用默认配置或自定义配置
        val finalConfig = config ?: ExportConfig(
            outputFile = finalOutputFile,
            outputSize = renderSize,
            videoBitRate = exportManager.getRecommendedBitRate(renderSize)
        )

        // 开始导出
        exportManager.startExport(
            segments = segments,
            config = finalConfig,
            listener = object : ExportListener {
                override fun onExportStart() {
                    mMainHandler.post {
                        exportListener?.onExportStart()
                    }
                }

                override fun onExportProgress(progress: Float) {
                    mMainHandler.post {
                        exportListener?.onExportProgress(progress)
                    }
                }

                override fun onExportComplete(outputFile: File) {
                    mMainHandler.post {
                        exportListener?.onExportComplete(outputFile)
                    }
                }

                override fun onExportError(error: Exception) {
                    mMainHandler.post {
                        exportListener?.onExportError(error)
                    }
                }
            }
        )
    }

    /**
     * 停止导出
     */
    fun stopExport() {
        exportManager.stopExport()
    }


    /**
     * 获取默认导出文件
     */
    private fun getDefaultExportFile(): File {
        // 获取应用的外部存储目录
        val fileName = exportManager.getRecommendedFileName()
        return File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            ), fileName
        )
    }

    override fun release() {
        playerThread?.release()
        videoRenderView?.releaseResources()
        exportManager.release()
    }

    override fun duration(): Long {
        if (durationUs == -1L) {
            durationUs = segments.sumOf {
                it.timelineRange.durationUs
            }
        }
        return durationUs
    }

    override fun setRenderSize(size: Size) {
        this.renderSize = size
        videoRenderView?.setTargetRenderSize(size)
    }

    override fun setLoop(loop: Boolean) {
        this.loop = loop
    }

    override fun setPlayerListener(listener: IPlayer.PlayerListener) {
        this.playListener = listener
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.what) {
            TYPE_PROGRESS -> {
                playUs = msg.obj as Long
                if (mMainHandler.hasMessages(TYPE_PROGRESS) == false) {
                    playListener?.onPositionChanged(playUs, duration())
                }
            }
        }
        return false
    }

}