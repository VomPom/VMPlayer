package com.vompom.vmplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.google.android.flexbox.FlexboxLayoutManager
import com.vompom.media.IPlayer
import com.vompom.media.effect.VMPlayerFactory
import com.vompom.media.effect.model.EffectType
import com.vompom.media.effect.model.VideoEffectEntity
import com.vompom.media.effect.render.VMRenderSession
import com.vompom.media.effect.render.effect.GrayscaleEffect
import com.vompom.media.effect.render.effect.InvertEffect
import com.vompom.media.effect.render.effect.RGBEffect
import com.vompom.media.effect.render.effect.SepiaEffect
import com.vompom.media.effect.render.sticker.StickerEffect
import com.vompom.media.export.ExportConfig
import com.vompom.media.export.ExportListener
import com.vompom.media.model.AudioMixConfig
import com.vompom.media.model.AudioTrackInputConfig
import com.vompom.media.model.ClipAsset
import com.vompom.media.model.TimeRange
import com.vompom.media.model.VolumeRamp
import com.vompom.media.utils.formatTimeFromUs
import com.vompom.vmplayer.adapter.ActionAdapter
import com.vompom.vmplayer.adapter.ActionItem
import com.vompom.vmplayer.adapter.EffectAdapter
import com.vompom.vmplayer.databinding.ActivityMediaBinding
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var player: IPlayer
    private lateinit var effectAdapter: EffectAdapter
    private lateinit var actionAdapter: ActionAdapter
    private val renderSession = VMRenderSession.createRenderSession()
    /** 记录当前已添加的贴纸 ID 列表，用于逐个移除 */
    private val stickerIds = mutableListOf<Long>()
    /** 当前是否正在播放 */
    private var isPlaying = false

    /** 当前是否已添加 BGM */
    private var isBgmAdded = false

    /** 当前是否正在拖动进度条，拖动期间不更新进度条位置 */
    private var isSeeking = false

    companion object {
        // 功能按钮 ID 常量
        const val ACTION_STOP = "stop"
        const val ACTION_EXPORT = "export"
        const val ACTION_ADD_STICKER = "add_sticker"
        const val ACTION_CLEAR_STICKER = "clear_sticker"
        const val ACTION_ADD_BGM = "add_bgm"
        const val ACTION_REMOVE_BGM = "remove_bgm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResUtils.init(this)
        setContentView(getContentView())
        initView()
    }

    private fun getContentView(): View {
        binding = ActivityMediaBinding.inflate(layoutInflater)
        return binding.root
    }

    private fun initView() {
        initPlayer()
        initPlayPauseControl()
        initEffectsRecyclerView()
        initActionsRecyclerView()
        initSeekBar()
    }

    /**
     * 初始化播放/暂停图标按钮
     */
    private fun initPlayPauseControl() {
        updatePlayPauseIcon()
        binding.ivPlayPause.setOnClickListener {
            if (isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.play()
                isPlaying = true
            }
            updatePlayPauseIcon()
        }
    }

    /**
     * 根据播放状态更新图标
     */
    private fun updatePlayPauseIcon() {
        binding.ivPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    /**
     * 初始化进度条
     */
    private fun initSeekBar() {
        binding.playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 将进度百分比转换为播放时间（微秒）
                    val duration = player.duration()
                    val targetUs = (progress.toLong() * duration) / 100
                    player.seekTo(targetUs)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
                player.pause()
                isPlaying = false
                updatePlayPauseIcon()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                // 松手后从当前拖动位置开始播放
                val progress = seekBar?.progress ?: 0
                val duration = player.duration()
                val targetUs = (progress.toLong() * duration) / 100
                player.seekTo(targetUs)
                player.play()
                isPlaying = true
                updatePlayPauseIcon()
            }
        })
    }

    /**
     * 初始化特效 RecyclerView
     */
    private fun initEffectsRecyclerView() {
        effectAdapter = EffectAdapter(
            onEffectSelected = { it ->
                onEffectAdded(it)
            },
            onEffectRemoved = { it ->
                renderSession.removeEffect(it)
            }
        ).apply {
            updateEffects(
                listOf(
                    VideoEffectEntity(EffectType.NONE, "无特效", null),
                    VideoEffectEntity(EffectType.GRAYSCALE, "黑白滤镜", GrayscaleEffect::class.java),
                    VideoEffectEntity(EffectType.SEPIA, "复古滤镜", SepiaEffect::class.java),
                    VideoEffectEntity(EffectType.INVERT, "反相效果", InvertEffect::class.java),
                    VideoEffectEntity(EffectType.RGB_ADJUST, "RGB调整", RGBEffect::class.java)
                )
            )
        }

        binding.rvEffects.apply {
            layoutManager = FlexboxLayoutManager(this@MainActivity)
            adapter = effectAdapter
        }
    }

    /**
     * 初始化功能按钮 RecyclerView
     */
    private fun initActionsRecyclerView() {
        actionAdapter = ActionAdapter { action ->
            onActionClicked(action)
        }.apply {
            updateActions(
                listOf(
                    ActionItem(ACTION_STOP, "停止"),
                    ActionItem(ACTION_EXPORT, "导出"),
                    ActionItem(ACTION_ADD_STICKER, "添加贴纸"),
                    ActionItem(ACTION_CLEAR_STICKER, "清除贴纸"),
                    ActionItem(ACTION_ADD_BGM, "添加BGM"),
                    ActionItem(ACTION_REMOVE_BGM, "移除BGM")
                )
            )
        }

        binding.rvActions.apply {
            layoutManager = FlexboxLayoutManager(this@MainActivity)
            adapter = actionAdapter
        }
    }

    /**
     * 处理功能按钮点击事件
     */
    private fun onActionClicked(action: ActionItem) {
        when (action.id) {
            ACTION_STOP -> {
                player.stop()
                isPlaying = false
                updatePlayPauseIcon()
            }
            ACTION_EXPORT -> startExport()
            ACTION_ADD_STICKER -> addSticker()
            ACTION_CLEAR_STICKER -> clearStickers()
            ACTION_ADD_BGM -> addBgm()
            ACTION_REMOVE_BGM -> removeBgm()
        }
    }

    private fun onEffectAdded(it: VideoEffectEntity) {
        if (it.type == EffectType.NONE) {
            effectAdapter.apply {
                getSelectedEffects().forEach { renderSession.removeEffect(it) }
                clearSelections()
                selectPosition(0)
            }
        } else {
            renderSession.addEffect(it)
            effectAdapter.deselectPosition(0)
        }
    }

    private fun initPlayer() {
        player = VMPlayerFactory.create(binding.flPlayer, renderSession)
        player.setRenderSize(Size(1280, 720))
        player.setPlayList(
            listOf(
                ClipAsset(ResUtils.testHok, TimeRange.create(2f, 5f)),
                // fixme:: 处理不同方向(尺寸/比例)视频的兼容
                ClipAsset(ResUtils.testHokV, TimeRange.create(3f, 2f)),
                ClipAsset(ResUtils.video10s, TimeRange.create(2f, 2f)),
                ClipAsset(ResUtils.testWz, TimeRange.create(2f, 3f)),
            ),
        )
        player.setPlayerListener(object : IPlayer.PlayerListener {
            @SuppressLint("SetTextI18n")
            override fun onPositionChanged(currentDurationUs: Long, playerDurationUs: Long) {
                Handler(Looper.getMainLooper()).post {
                    val currentTime = formatTimeFromUs(currentDurationUs)
                    val totalTime = formatTimeFromUs(playerDurationUs)
                    binding.tvTime.text = "$currentTime / $totalTime"

                    // 拖动进度条期间不更新进度条位置，避免回调覆盖用户拖动的位置
                    if (!isSeeking) {
                        val progressPercent = if (playerDurationUs > 0) {
                            ((currentDurationUs.toFloat() / playerDurationUs) * 100).toInt()
                        } else {
                            0
                        }
                        binding.playProgress.progress = progressPercent
                        binding.playProgress.max = 100
                    }
                }
            }
        })
        renderSession.bindPlayer(player)
    }

    @SuppressLint("SetTextI18n")
    private fun startExport() {
        val outputFile = File(
            getExternalFilesDir("exports"),
            "custom_export_${System.currentTimeMillis()}.mp4"
        )

        val config = ExportConfig(
            outputFile = outputFile,
            outputSize = Size(1280, 720),
            videoBitRate = 2000000,
            frameRate = 30
        )
        player.pause()
        isPlaying = false
        updatePlayPauseIcon()

        player.createExporter().export(outputFile, config, object : ExportListener {
            override fun onExportStart() {}

            override fun onExportProgress(progress: Float) {
                Handler(Looper.getMainLooper()).post {
                    actionAdapter.updateActionText(
                        ACTION_EXPORT,
                        "${java.lang.String.format("%.2f", progress * 100)}%"
                    )
                }
            }

            override fun onExportComplete(outputFile: File) {
                Handler(Looper.getMainLooper()).post {
                    actionAdapter.updateActionText(ACTION_EXPORT, "导出")
                }
            }

            override fun onExportError(error: Exception) {}
        })
    }

    /**
     * 添加贴纸：从 sticker 文件夹中随机选择一张，随机位置
     */
    private fun addSticker() {
        val stickerPath = ResUtils.getRandomStickerPath() ?: return
        // 随机生成贴纸位置，避免完全重叠
        val posX = (Math.random() * 0.6f).toFloat() + 0.1f
        val posY = (Math.random() * 0.6f).toFloat() + 0.1f
        val sticker = StickerEffect(stickerPath, posX, posY, 0.2f, 0.2f, 1.0f)
        stickerIds.add(sticker.stickerId)
        renderSession.addSticker(sticker)
    }

    /**
     * 清除所有贴纸
     */
    private fun clearStickers() {
        renderSession.clearStickers()
        stickerIds.clear()
    }

    /**
     * 添加 BGM：使用 30s.mp4 的音频轨道作为背景音乐
     * 原始音频降到 40%，BGM 音量 60%，前 2 秒淡入
     */
    private fun addBgm() {
        if (isBgmAdded) return

        val audioMixConfig = AudioMixConfig().apply {
            originalVolume = 0.4f
            addTrack(
                AudioTrackInputConfig(
                    trackId = 1,
                    filePath = ResUtils.bgm2,
                    volume = 0.4f,
                    loop = true,
                    volumeRamps = listOf(
                        VolumeRamp(0L, 2_000_000L, 0f, 0.6f) // 前 2 秒淡入
                    )
                )
            )
            addTrack(
                AudioTrackInputConfig(
                    trackId = 2,
                    filePath = ResUtils.bgm,
                    volume = 0.5f,
                    loop = true
                )
            )
        }
        player.setAudioMix(audioMixConfig)
        isBgmAdded = true
        actionAdapter.updateActionText(ACTION_ADD_BGM, "BGM已添加")
    }

    /**
     * 移除 BGM，恢复原始音频
     */
    private fun removeBgm() {
        if (!isBgmAdded) return
        player.removeAudioMix()
        isBgmAdded = false
        actionAdapter.updateActionText(ACTION_ADD_BGM, "添加BGM")
    }

    override fun onPause() {
        super.onPause()
        player.pause()
        isPlaying = false
        updatePlayPauseIcon()
    }

    override fun onResume() {
        super.onResume()
        player.play()
        isPlaying = true
        updatePlayPauseIcon()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}