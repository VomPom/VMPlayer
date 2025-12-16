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
import com.vompom.media.VMPlayer
import com.vompom.media.export.Exporter.ExportConfig
import com.vompom.media.export.Exporter.ExportListener
import com.vompom.media.model.ClipAsset
import com.vompom.media.model.EffectType
import com.vompom.media.model.TimeRange
import com.vompom.media.model.VideoEffectEntity
import com.vompom.media.render.VMRenderSession
import com.vompom.media.render.effect.GrayscaleEffect
import com.vompom.media.render.effect.InvertEffect
import com.vompom.media.render.effect.RGBEffect
import com.vompom.media.render.effect.SepiaEffect
import com.vompom.media.utils.formatTimeFromUs
import com.vompom.vmplayer.adapter.EffectAdapter
import com.vompom.vmplayer.databinding.ActivityMediaBinding
import java.io.File


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var player: IPlayer
    private lateinit var effectAdapter: EffectAdapter
    private val renderSession = VMRenderSession.createRenderSession()

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

        binding.btnPlay.setOnClickListener {
            player.play()
        }
        binding.btnPause.setOnClickListener {
            player.pause()
        }
        binding.btnStop.setOnClickListener {
            player.stop()
        }
        binding.btnExport.setOnClickListener {
            startExport()
        }

        initEffectsRecyclerView()

        binding.playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // 简化处理
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player.play()
            }
        })
    }

    /**
     * 初始化特效RecyclerView
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
        player = VMPlayer.create(binding.flPlayer, renderSession)
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

                    val progressPercent = if (playerDurationUs > 0) {
                        ((currentDurationUs.toFloat() / playerDurationUs) * 100).toInt()
                    } else {
                        0
                    }
                    binding.playProgress.progress = progressPercent
                    binding.playProgress.max = 100
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

        player.createExporter().export(outputFile, config, object : ExportListener {
            override fun onExportStart() {}

            override fun onExportProgress(progress: Float) {
                binding.btnExport.text = "${java.lang.String.format("%.2f", progress * 100)}%"
            }

            override fun onExportComplete(outputFile: File) {
                binding.btnExport.text = "导出"
            }

            override fun onExportError(error: Exception) {}
        })
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}