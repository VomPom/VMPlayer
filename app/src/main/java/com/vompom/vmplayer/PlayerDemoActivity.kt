package com.vompom.vmplayer

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.vompom.media.IPlayer
import com.vompom.media.VMPlayer
import com.vompom.media.docode.model.ClipAsset
import com.vompom.media.docode.model.TimeRange
import com.vompom.media.export.EncodeManager.ExportConfig
import com.vompom.media.export.EncodeManager.ExportListener
import com.vompom.media.utils.usToS
import com.vompom.vmplayer.databinding.ActivityMediaBinding
import java.io.File

/**
 *
 * Created by @juliswang on 2025/09/25 11:11
 *
 * @Description
 */

class PlayerDemoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaBinding
    private lateinit var player: IPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ResUtils.init(this)
        setContentView(getContentView())
        initView()
    }

    fun initView() {
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
        binding.playProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    player.seekTo(seekBar?.progress?.toLong() ?: 0L)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player.play()
            }

        })
    }

    fun getContentView(): View {
        binding = ActivityMediaBinding.inflate(layoutInflater)
        return binding.root
    }

    private fun initPlayer() {
        player = VMPlayer.Companion.create(binding.flPlayer)
        player.setRenderSize(Size(1280, 720))
        player.setPlayList(
            listOf(
                ClipAsset(ResUtils.testHok, TimeRange.create(0f, 2f)),
//                ClipAsset(ResUtils.testHok, TimeRange.create(2f, 2f)),
//                ClipAsset(ResUtils.testHok, TimeRange.create(0f, 2f)),
//                ClipAsset(ResUtils.testHokV, TimeRange.create(3f, 2f)),
//                ClipAsset(ResUtils.video10s, TimeRange.Companion.create(2f, 2f)),
//                ClipAsset(ResUtils.testWz, TimeRange.create(2f, 3f)),
            ),
        )
        player.setPlayerListener(object : IPlayer.PlayerListener {
            @SuppressLint("SetTextI18n")
            override fun onPositionChanged(currentDurationUs: Long, playerDurationUs: Long) {
                ThreadUtil.runOnMain {
                    binding.tvTime.text =
                        "${usToS(currentDurationUs)} / ${
                            usToS(
                                playerDurationUs
                            )
                        }".toString()
                    binding.playProgress.progress = currentDurationUs.toInt()
                    binding.playProgress.max = playerDurationUs.toInt()
                }
            }

        })
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun startExport() {
        // 创建自定义导出配置
        val outputFile = File(
            getExternalFilesDir("exports"),
            "custom_export_${System.currentTimeMillis()}.mp4"
        )

        val config = ExportConfig(
            outputFile = outputFile,
            outputSize = Size(1280, 720),
            videoBitRate = 2000000, // 2Mbps
            frameRate = 30
        )
        player.pause()
        // 开始导出
        player.export(outputFile, config, object : ExportListener {
            override fun onExportStart() {

            }

            override fun onExportProgress(progress: Float) {
                binding.btnExport.text = "${String.format("%.2f", progress * 100)}%"
            }

            override fun onExportComplete(outputFile: File) {
                binding.btnExport.text = "Export"
            }

            override fun onExportError(error: Exception) {

            }

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