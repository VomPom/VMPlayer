package com.vompom.media

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Size
import android.view.Surface
import android.view.View
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
import com.vompom.media.player.PlayerThread
import com.vompom.media.render.GLSurfacePlayerView
import com.vompom.media.render.IPlayerView
import com.vompom.media.render.PlayerRender
import com.vompom.media.render.TexturePlayerView
import com.vompom.media.render.effect.SimpleVideoEffectProcessor

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
    private var renderSize = Size(DEFAULT_RENDER_WIDTH, DEFAULT_RENDER_HEIGHT)
    private var playUs: Long = 0L
    private var playerView: IPlayerView? = null

    private var useTextureView = true // 默认使用TextureView, false GLSurfaceView

    companion object {
        const val TYPE_STATES: Int = 1
        const val TYPE_PROGRESS: Int = 2
        const val TYPE_VIEWPORT_UPDATE: Int = 3
        const val DEFAULT_RENDER_WIDTH = 1280
        const val DEFAULT_RENDER_HEIGHT = 720

        fun create(frameLayout: FrameLayout): VMPlayer {
            return VMPlayer(frameLayout)
        }
    }

    private constructor(playerContainer: FrameLayout) {
        initContentView(playerContainer)
    }

    private fun initContentView(playerContainer: FrameLayout) {
        val playerView = createPlayerView(playerContainer.context)
        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun createPlayerView(context: Context): View {
        return if (useTextureView) {
            createTexturePlayerView(context)
        } else {
            createGLPlayerView(context)
        }
    }

    private fun createGLPlayerView(context: Context): GLSurfacePlayerView {
        return GLSurfacePlayerView(context).apply {
            this.setRenderSize(renderSize)
            setSurfaceReadyCallback { surface ->
                onSurfaceCreate(surface)
            }

            setEffectProcessor(SimpleVideoEffectProcessor())
        }
    }

    private fun createTexturePlayerView(context: Context): TexturePlayerView {
        val playerRender = PlayerRender().apply {
            initRenderSize(renderSize)
            setSurfaceReadyCallback { surface ->
                onSurfaceCreate(surface)
            }
        }
        return TexturePlayerView(context).apply {
            setRenderSize(renderSize)
            setRenderer(playerRender)
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
    }

    override fun setLoop(loop: Boolean) {
        this.loop = loop
    }

    override fun setPlayerListener(listener: IPlayer.PlayerListener) {
        this.playListener = listener
    }

    override fun createExporter(): IExporter = Exporter(segments)

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