# VMPlayer

> 这是一个 **个人学习项目**，是我在音视频播放器方向长期研究、动手实践与经验沉淀的产物。
>
> 初衷不是造一个能对标 ExoPlayer / IJKPlayer 的生产级播放器，是把自己对 **MediaCodec 编解码、OpenGL ES 渲染管线、音画同步、时间轴剪辑、特效链、导出复用渲染链** 等核心问题的理解，一步步用代码落地，形成一个可复盘、可扩展的轻量级 Android 媒体框架。
>
---

## 已实现的能力

- **多片段顺序播放**：基于 `TrackSegment` 时间轴，任意 `ClipAsset` 列表拼接播放
- **音视频解码与同步**：独立的 `VideoDecoder` / `AudioDecoder`，由 `AVSyncManager` 做音画同步
- **Seek 支持**：跨片段 seek、快速拖动、seek 后音画同步
- **OpenGL ES 渲染管线**：自建 `EglHelper` + `GLThread` + `PlayerRender`，解耦于系统 `GLSurfaceView`
- **特效链系统**（`EffectChainManager`）：多特效串联、动态增删、顺序可控
  - 内置滤镜：灰度、复古（Sepia）、反相、RGB 调整
  - 内置特效：贴纸（`StickerEffect`，支持位置、尺寸、透明度）
- **多轨道音频混音**（`AudioCompositor` + `AudioMixer`）
  - 原始音频 + 多路 BGM 叠加
  - 支持每轨音量、循环、**音量渐变（淡入淡出）**
  - 运行时动态增删混音配置
- **视频导出**（`IExporterFactory` + `vmplayer-effect` 侧 `Exporter`）：**复用同一条渲染链**，保证导出与预览画面完全一致
  - `MediaCodec` 硬编 + `MediaMuxer` 封装
  - 特效、贴纸、BGM 全部参与导出

---

## 效果演示

下面是在预览与导出流程中的效果示例（图片位于项目根目录下的 `.img` 文件夹）：

| 贴纸特效                      | 反相特效效果                   | 黑白滤镜效果                    | 多特效组合效果 |
|---------------------------|--------------------------|---------------------------| --- |
| ![复古特效](.img/sticker.png) | ![反相特效](.img/invert.png) | ![黑白滤镜效果](.img/black.png) | ![多特效组合效果](.img/more.png) |



## 架构概览

```
        ┌──────────────────────────── app ────────────────────────────────┐
        │   通过 VMPlayerFactory 组装播放器 + 注入渲染/导出实现                 │
        └──────────┬───────────────────────────────────────┬────────────────────┘
                   │                                            │
  ┌─────────────────▼─────────────────────┐  ┌───────▼────────────────────────────────┐
  │                vmplayer                  │  │        vmplayer-effect              │
  │  （视频编解码 / 音轨合成 / 播放调度）       │  │  （OpenGL / 特效 / 贴纸 / 渲染视图）        │
  │                                            │  │                                     │
  │  VMPlayer  ◄─── IRenderSurfaceProvider ◄─┼── RenderSurfaceProvider               │
  │            ◄─── IExporterFactory       ◄─┼── ExporterFactory + Exporter           │
  │     │                                      │  │           │                         │
  │     ▼                                      │  │           ▼                         │
  │ VideoDecoderTrack    AudioCompositor       │  │  PlayerView / PlayerRender          │
  │ （解码+seek）         （原声+BGM混音）           │  │  EffectChain / Sticker              │
  └────────────────────┬─────────────────────┘  └─────────────┬──────────────────────────┘
                      │ SurfaceTexture / PCM                │
                      ▼                                     ▼
                  AudioTrack                           屏幕 / MP4
                   播放                                 预览/导出
```

> 架构约束：vmplayer 不依赖 vmplayer-effect，单向依赖。effect 侧通过 `IRenderSurfaceProvider` / `IExporterFactory` 把 OpenGL 与导出能力注入回播放核心。
> 注：BGM / 音轨混音因与播放调度强相关，目前仍留在 `vmplayer`；后续可视情况移到 `vmplayer-effect` 。


---

## 目录结构

```
VMPlayer/
├── app/                               # 示例 App，演示播放、特效、贴纸、BGM、导出
│   └── src/main/java/com/vompom/vmplayer/MainActivity.kt
├── vmplayer/                          # 核心播放器模块（library）——只做视频编解码 / 音轨合成 / 播放调度
│   └── src/main/java/com/vompom/media/
│       ├── VMPlayer.kt                # 播放器门面（通过 IRenderSurfaceProvider / IExporterFactory 注入渲染与导出实现）
│       ├── IPlayer.kt                 # 对外 API 接口
│       ├── IRenderSurfaceProvider.kt  # 渲染侧抽象：供特效模块注入 Surface 与渲染链
│       ├── docode/                    # 解码：decorder / track
│       │   ├── decorder/              # BaseDecoder / VideoDecoder / AudioDecoder
│       │   └── track/                 # 多片段时间轴 + 音频合成（BGM 混音暂留此处）
│       ├── extractor/                 # MediaExtractor 封装
│       ├── player/                    # 播放线程、音画同步
│       ├── export/                    # 导出编解码器：reader / encoder / muxer + IExporterFactory / ExportConfig / ExportListener
│       ├── model/                     # 数据模型（ClipAsset、TimeRange、AudioMixConfig…）
│       └── utils/                     # 工具类（VLog、诊断工具…）
├── vmplayer-effect/                   # 特效模块（library）——OpenGL / 特效 / 贴纸 / 渲染视图 / 导出渲染链
│   └── src/main/java/com/vompom/media/effect/
│       ├── VMPlayerFactory.kt         # 组装入口：把 effect 的 Provider/Factory 注入到 VMPlayer
│       ├── IRenderSession.kt          # 渲染会话抽象
│       ├── player/                    # PlayerView / RenderSurfaceProvider
│       ├── render/                    # EGL / GLThread / PlayerRender / EffectChainManager
│       │   ├── effect/                # 滤镜（灰度、复古、反相、RGB）
│       │   └── sticker/               # 贴纸特效
│       ├── export/                    # Exporter + ExporterFactory（复用预览渲染链）
│       ├── model/                     # 渲染相关模型（RenderModel、VideoEffectEntity、EffectType、Sticker、TextureInfo）
│       └── utils/                     # GLUtils 等 OpenGL 辅助工具
├── docs/                              # 设计文档与 TODO
├── .docs/functions.md                 # 后续功能规划与优先级矩阵
└── deps.gradle                        # 统一依赖管理
```

---

## 快速上手

### 1. 构建

```bash
./gradlew :app:assembleDebug
```

环境要求：
- Android Studio（AGP 7.3.1）
- JDK 1.8
- Kotlin 2.1.x

### 2. 最小使用示例

```kotlin
// 1. 创建渲染会话（特效模块，持有特效链、贴纸等渲染层资源）
val renderSession = VMRenderSession.createRenderSession()

// 2. 通过特效模块的 VMPlayerFactory 创建播放器（挂到一个 FrameLayout 容器）
//    VMPlayerFactory 位于 vmplayer-effect，负责把渲染/导出实现注入给 vmplayer
val player = VMPlayerFactory.create(playerContainer, renderSession)
player.setRenderSize(Size(1280, 720))

// 3. 设置播放列表（多个片段会顺序拼接播放）
player.setPlayList(
    listOf(
        ClipAsset(pathA, TimeRange.create(2f, 5f)),  // 从第 2s 开始，截 5s
        ClipAsset(pathB, TimeRange.create(0f, 3f)),
    )
)

// 4. 进度回调
player.setPlayerListener(object : IPlayer.PlayerListener {
    override fun onPositionChanged(currentUs: Long, totalUs: Long) { /* ... */ }
})

// 5. 播放控制
player.play()
player.seekTo(3_000_000L)   // 跳到 3s
player.pause()
player.release()
```

### 3. 添加特效 / 贴纸

```kotlin
// 添加灰度滤镜
renderSession.addEffect(
    VideoEffectEntity(EffectType.GRAYSCALE, "黑白", GrayscaleEffect::class.java)
)

// 添加一张贴纸
val sticker = StickerEffect(stickerPath, posX = 0.3f, posY = 0.3f, w = 0.2f, h = 0.2f, alpha = 1f)
renderSession.addSticker(sticker)
```

### 4. 添加 BGM（多轨道混音 + 淡入）

```kotlin
val mix = AudioMixConfig().apply {
    originalVolume = 0.4f
    addTrack(
        AudioTrackInputConfig(
            trackId = 1,
            filePath = bgmPath,
            volume = 0.6f,
            loop = true,
            volumeRamps = listOf(VolumeRamp(0L, 2_000_000L, 0f, 0.6f)) // 前 2s 淡入
        )
    )
}
player.setAudioMix(mix)
```

### 5. 导出（复用同一条渲染链）

```kotlin
val config = ExportConfig(
    outputFile = outputFile,
    outputSize = Size(1280, 720),
    videoBitRate = 2_000_000,
    frameRate = 30
)

player.createExporter().export(outputFile, config, object : ExportListener {
    override fun onExportStart() {}
    override fun onExportProgress(progress: Float) {}
    override fun onExportComplete(file: File) {}
    override fun onExportError(e: Exception) {}
})
```



## 已踩过的坑 / 已解决的问题

- 多片段解码切换时的状态残留与资源释放顺序
- 音画线程同步（`AVSyncManager` 的演进）
- 跨片段 seek、快速拖动下的音画一致性
- 画布大小与视频源尺寸的视口适配
- 导出音频时长不准（PTS 与采样数对不齐）
- 视频预览添加的特效如何一致地走进导出链路
- 多片段切换时音频轨道的衔接与瞬断

---

## 后续计划（Roadmap）

- 多轨道音频/视频播放优化处理
- 亮度/对比度/饱和度、高斯模糊、暗角、马赛克、视频旋转翻转、特效时间范围控制
- 贴纸手势交互、视频区间变速播放、视频裁剪、帧截图 / 缩略图
- 转场、文字叠加 / 动态字幕、画中画、PAG 特效
- 软硬解兼容切换
