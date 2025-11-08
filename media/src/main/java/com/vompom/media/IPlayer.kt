package com.vompom.media

import android.util.Size
import com.vompom.media.export.ExportManager.ExportConfig
import com.vompom.media.export.ExportManager.ExportListener
import com.vompom.media.model.ClipAsset
import java.io.File

/**
 *
 * Created by @juliswang on 2025/09/28 20:08
 *
 * @Description 定义播放器提供的接口
 */

interface IPlayer {
    fun setPlayList(videoList: List<ClipAsset>)
    fun play()
    fun pause()
    fun seekTo(positionUs: Long)
    fun stop()
    fun export(outputFile: File?, config: ExportConfig? = null, listener: ExportListener?)
    fun release()
    fun duration(): Long
    fun setLoop(loop: Boolean)
    fun setRenderSize(size: Size)
    fun setPlayerListener(listener: PlayerListener)
    interface PlayerListener {

        fun onPositionChanged(currentDurationUs: Long, playerDurationUs: Long)

    }
}