package com.vompom.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import com.vompom.media.docode.decorder.AudioDecoder
import com.vompom.media.docode.decorder.VideoDecoder
import com.vompom.media.docode.track.AudioDecoderTrack
import com.vompom.media.docode.track.VideoDecoderTrack
import com.vompom.media.export.Exporter
import com.vompom.media.export.IExporter
import com.vompom.media.model.ClipAsset
import com.vompom.media.model.TrackSegment
import com.vompom.media.player.IPlayerView
import com.vompom.media.player.PlayerThread
import com.vompom.media.player.PlayerView
import com.vompom.media.render.PlayerRender

/**
 *
 * Created by @juliswang on 2025/09/25 20:42
 *
 * @Description 基于 [VideoDecoder] [AudioDecoder] 包装播放器，协调整个播放流程，管理播放状态
 *
 */
class VMPlayer : IPlayer, Handler.Callback {
    private val renderSession: IRenderSession
    private var segments: List<TrackSegment> = emptyList()
    private var durationUs: Long = -1L

    private var playerThread: PlayerThread? = null
    private var playListener: IPlayer.PlayerListener? = null
    var mMainHandler: Handler = Handler(Looper.getMainLooper(), this)

    private var loop = true
    private var renderSize = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)
    private var playUs: Long = 0L
    private var playerView: IPlayerView? = null

    companion object {
        const val TYPE_STATES: Int = 1
        const val TYPE_PROGRESS: Int = 2
        const val TYPE_VIEWPORT_UPDATE: Int = 3
        const val DEFAULT_RENDER_WIDTH = 1280
        const val DEFAULT_RENDER_HEIGHT = 720

        fun create(frameLayout: FrameLayout, renderSession: IRenderSession): VMPlayer {
            return VMPlayer(frameLayout, renderSession)
        }
    }

    private constructor(playerContainer: FrameLayout, renderSession: IRenderSession) {
        this.renderSession = renderSession
        initContentView(playerContainer)
    }

    private fun initContentView(playerContainer: FrameLayout) {
        val playerView = createTexturePlayerView(playerContainer.context)
        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun createTexturePlayerView(context: Context): PlayerView {
        val playerRender = createRender()
        val playerView = PlayerView(context).apply {
            setRenderSize(renderSize)
            setRenderer(playerRender)
        }
        renderSession.attachRenderChain(playerView.getGLThread(), playerRender)
        return playerView
    }

    private fun createRender(): PlayerRender {
        return PlayerRender().apply {
            initRenderSize(renderSize)
            setSurfaceReadyCallback { surface ->
                onSurfaceCreate(surface)
            }
        }
    }

    private fun onSurfaceCreate(surface: Surface) {
        val videoTrack = VideoDecoderTrack(segments, surface)
        val audioTrack = AudioDecoderTrack(segments)
        videoTrack.setVideoSizeChangeListener { videoSize ->
            playerView?.updateVideoSize(videoSize)
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
        playerView?.release()
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
        playerView?.setRenderSize(size)
        renderSession.updateRenderSize(size)
    }

    override fun setLoop(loop: Boolean) {
        this.loop = loop
    }

    override fun setPlayerListener(listener: IPlayer.PlayerListener) {
        this.playListener = listener
    }

    /**
     * fixme:这里待完善，目前通过 renderSession 作为中间层，获取渲染数据，maybe 有更好的方式
     *
     * @return
     */
    override fun createExporter(): IExporter {
        val renderModel = renderSession.getRenderModel()
        return Exporter(segments, renderModel)
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