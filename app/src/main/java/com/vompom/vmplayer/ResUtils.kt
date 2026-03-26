package com.vompom.vmplayer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 *
 * Created by @juliswang on 2025/09/16 20:01
 *
 * @Description
 */

object ResUtils {

    private const val TAG = "Resources"
    private const val ASSETS_MEDIA_PATH = "media"
    private const val SANDBOX_MEDIA_DIR = "media_files"
    private const val STICKER_SUB_DIR = "sticker"

    // 缓存已复制的文件路径（key 为相对于 media 的路径，如 "hok.mp4" 或 "sticker/dog.png"）
    private val copiedFilesCache = mutableMapOf<String, String>()
    // 缓存贴纸文件路径列表
    private val stickerPaths = mutableListOf<String>()
    private var isInitialized = false


    /**
     * 初始化资源文件，将 assets/media 目录下的所有文件复制到应用沙盒目录
     * @param context 应用上下文
     * @return 是否初始化成功
     */
    fun init(context: Context): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Media resources already initialized")
            return true
        }

        try {
            val assetsManager = context.assets
            val mediaFiles = assetsManager.list(ASSETS_MEDIA_PATH) ?: emptyArray()

            if (mediaFiles.isEmpty()) {
                Log.w(TAG, "No media files found in assets/$ASSETS_MEDIA_PATH")
                return false
            }

            // 创建沙盒目录
            val sandboxDir = File(context.filesDir, SANDBOX_MEDIA_DIR)
            if (!sandboxDir.exists()) {
                sandboxDir.mkdirs()
            }

            // 递归复制所有文件（包括子目录）
            val copiedCount = copyAssetDirRecursive(context, ASSETS_MEDIA_PATH, sandboxDir, "")

            isInitialized = true
            Log.i(TAG, "Media resources initialization completed. Copied $copiedCount files.")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing media resources", e)
            return false
        }
    }

    /**
     * 递归复制 assets 目录下的所有文件到沙盒目录
     * @param context 应用上下文
     * @param assetDir assets 中的目录路径
     * @param sandboxBaseDir 沙盒基础目录
     * @param relativePath 相对于 media 的路径前缀（用于缓存 key）
     * @return 成功复制的文件数量
     */
    private fun copyAssetDirRecursive(
        context: Context,
        assetDir: String,
        sandboxBaseDir: File,
        relativePath: String
    ): Int {
        val assetsManager = context.assets
        val children = assetsManager.list(assetDir) ?: return 0
        var copiedCount = 0

        // 确保当前目标目录存在
        val currentTargetDir = if (relativePath.isEmpty()) sandboxBaseDir
        else File(sandboxBaseDir, relativePath).also { it.mkdirs() }

        for (child in children) {
            val assetChildPath = "$assetDir/$child"
            val relativeChildPath = if (relativePath.isEmpty()) child else "$relativePath/$child"

            // 判断是否为目录：如果 list 返回非空数组则为目录
            val subChildren = assetsManager.list(assetChildPath)
            if (subChildren != null && subChildren.isNotEmpty()) {
                // 是子目录，递归复制
                copiedCount += copyAssetDirRecursive(context, assetChildPath, sandboxBaseDir, relativeChildPath)
            } else {
                // 是文件，复制
                val success = copyAssetFile(context, assetChildPath, currentTargetDir, child)
                if (success) {
                    val filePath = File(currentTargetDir, child).absolutePath
                    copiedFilesCache[relativeChildPath] = filePath
                    // 如果是贴纸目录下的文件，加入贴纸列表
                    if (relativeChildPath.startsWith("$STICKER_SUB_DIR/")) {
                        stickerPaths.add(filePath)
                    }
                    Log.d(TAG, "Successfully copied $relativeChildPath to $filePath")
                    copiedCount++
                } else {
                    Log.e(TAG, "Failed to copy $relativeChildPath")
                }
            }
        }
        return copiedCount
    }

    /**
     * 复制单个 asset 文件到目标目录
     * @param context 应用上下文
     * @param assetPath assets 中的完整路径
     * @param targetDir 目标目录
     * @param fileName 文件名
     * @return 是否复制成功
     */
    private fun copyAssetFile(context: Context, assetPath: String, targetDir: File, fileName: String): Boolean {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            inputStream = context.assets.open(assetPath)

            val targetFile = File(targetDir, fileName)
            outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            return true

        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset file $assetPath", e)
            return false
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }

    /**
     * 通过文件名获取沙盒目录中的文件绝对路径
     * @param fileName 文件名
     * @return 文件的绝对路径，如果文件不存在则返回 null
     */
    fun getFilePath(fileName: String): String? = copiedFilesCache[fileName]

    // 使用 lazy 初始化，只有在调用时才获取路径
    val testHok: String by lazy { getFilePath("hok.mp4")!! }
    val testHokV: String by lazy { getFilePath("hok_v.mp4")!! }
    val testWz: String by lazy { getFilePath("wz.mp4")!! }
    val video30s: String by lazy { getFilePath("30s.mp4")!! }
    val video10s: String by lazy { getFilePath("10s.mp4")!! }
    val h264: String by lazy { getFilePath("h264.h264")!! }

    /**
     * 从贴纸文件夹中随机获取一张贴纸的路径
     * @return 随机贴纸的绝对路径，如果没有贴纸则返回 null
     */
    fun getRandomStickerPath(): String? {
        if (stickerPaths.isEmpty()) return null
        return stickerPaths.random()
    }

    /**
     * 获取所有贴纸路径列表
     */
    fun getAllStickerPaths(): List<String> = stickerPaths.toList()
}