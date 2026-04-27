package com.vompom.media

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import com.vompom.media.docode.track.AudioCompositionTrack
import com.vompom.media.docode.track.VideoDecoderTrack
import com.vompom.media.export.IExporter
import com.vompom.media.export.IExporterFactory
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.ClipAsset
import com.vompom.media.model.TrackSegment
import com.vompom.media.player.PlayerThread

/**
 *
 * Created by @juliswang on 2025/09/25 20:42
 *
 * @Description 基于 [com.vompom.media.docode.decorder.VideoDecoder] [com.vompom.media.docode.decorder.AudioDecoder]
 *              包装的播放器，协调整个播放流程，管理播放状态。
 *
 *              该类仅负责「视频编解码 + 音频合成 + 播放时序调度」，
 *              所有 OpenGL / 特效 / 贴纸 / View 相关逻辑由 `vmplayer-effect` 模块通过
 *              [IRenderSurfaceProvider] 与 [IExporterFactory] 注入。
 */
class VMPlayer : IPlayer, Handler.Callback {
    private val surfaceProvider: IRenderSurfaceProvider
    private val exporterFactory: IExporterFactory
    private var segments: List<TrackSegment> = emptyList()
    private var durationUs: Long = -1L

    private var playerThread: PlayerThread? = null
    private var playListener: IPlayer.PlayerListener? = null
    var mMainHandler: Handler = Handler(Looper.getMainLooper(), this)

    private var loop = true
    private var renderSize = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)
    private var playUs: Long = 0L
    private var audioMixConfig: AudioMixConfig? = null
    // 持有当前正在使用的 AudioCompositionTrack 引用，用于动态更新混音配置
    private var currentAudioTrack: AudioCompositionTrack? = null

    companion object {
        const val TYPE_STATES: Int = 1
        const val TYPE_PROGRESS: Int = 2
        const val TYPE_VIEWPORT_UPDATE: Int = 3
        const val DEFAULT_RENDER_WIDTH = 1280
        const val DEFAULT_RENDER_HEIGHT = 720

        /**
         * 核心构造入口：由 `vmplayer-effect` 模块的高层工厂调用，传入已经准备好的
         * [IRenderSurfaceProvider]（带 OpenGL / 特效能力）和 [IExporterFactory]（带渲染链的导出器）。
         */
        fun create(
            surfaceProvider: IRenderSurfaceProvider,
            exporterFactory: IExporterFactory
        ): VMPlayer = VMPlayer(surfaceProvider, exporterFactory)
    }

    private constructor(
        surfaceProvider: IRenderSurfaceProvider,
        exporterFactory: IExporterFactory
    ) {
        this.surfaceProvider = surfaceProvider
        this.exporterFactory = exporterFactory
        // 绑定 Surface 就绪回调：当特效侧的 OES SurfaceTexture 创建完毕后，
        // 会把用于解码器输出的 Surface 传给我们。
        this.surfaceProvider.setOnSurfaceReady { surface ->
            onSurfaceCreate(surface)
        }
    }

    private fun onSurfaceCreate(surface: Surface) {
        val videoTrack = VideoDecoderTrack(segments, surface)
        val audioTrack = AudioCompositionTrack(segments, audioMixConfig)
        currentAudioTrack = audioTrack
        videoTrack.setVideoSizeChangeListener { videoSize ->
            surfaceProvider.updateVideoSize(videoSize)
        }
        playerThread = PlayerThread(this, videoTrack, audioTrack).apply {
            sendMessage(PlayerThread.Companion.ACTION_PREPARE)
        }
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

    override fun release() {
        playerThread?.release()
        surfaceProvider.release()
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
        surfaceProvider.setRenderSize(size)
    }

    override fun setLoop(loop: Boolean) {
        this.loop = loop
    }

    override fun setPlayerListener(listener: IPlayer.PlayerListener) {
        this.playListener = listener
    }

    override fun setAudioMix(config: AudioMixConfig) {
        this.audioMixConfig = config
        // 动态更新正在运行的 AudioCompositionTrack
        currentAudioTrack?.updateAudioMixConfig(config)
    }

    override fun removeAudioMix() {
        this.audioMixConfig = null
        // 动态清除正在运行的 AudioCompositionTrack 的混音配置
        currentAudioTrack?.clearAudioMixConfig()
    }

    /**
     * 通过注入进来的 [IExporterFactory] 创建导出器，
     * 核心模块不感知具体的渲染/特效逻辑。
     */
    override fun createExporter(): IExporter {
        return exporterFactory.create(segments, audioMixConfig)
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