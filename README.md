# TVRemoteIMENext（小盒精灵Next）

[![build](https://github.com/gmcf111/TVRemoteIMENext/actions/workflows/build.yml/badge.svg)](https://github.com/gmcf111/TVRemoteIMENext/actions/workflows/build.yml)

通过浏览器遥控电视盒子的安卓应用：输入法（IME）+ HTTP 控制服务 + 触控板 + DLNA 投屏 + 边下边播（迅雷 P2SP/BT/ED2K）+ 可选 Root 增强。**Kotlin 重写版**。

- 上游：[kingthy/TVRemoteIME](https://gitee.com/kingthy/TVRemoteIME) v1.4.1（GPL v2），融合 [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME) 的触控板功能
- 许可证：**GPL v2**（衍生作品，随源分发）

## 技术要点

| 项 | 值 |
|---|---|
| 语言 | Kotlin（业务层 100%），thunder JNI 参数类保持 Java |
| minSdk / targetSdk | 19（Android 4.4）/ 35 |
| ABI | **仅 armeabi-v7a**（32 位盒子生态） |
| UI | Material Components（View 体系），主题色可自定义 |
| 构建 | Gradle 8.13 / AGP 8.13.2 / JDK 17 |

## 构建

```bash
./gradlew assembleDebug      # debug APK（app/build/outputs/apk/debug/）
./gradlew lint
```

## 仓库结构

```
app/          主应用：IME、HTTP server、四通道控制、DLNA、UI
adb-lib/      ADB 协议库（Kotlin 重写）
ijkplayer/    ijkplayer 播放封装（so 原样保留，Java 层 AndroidX 化）
thunder/      迅雷下载封装（Java + so 原样保留，GPL v2 义务随源分发）
```

## 致谢

- [kingthy/TVRemoteIME](https://gitee.com/kingthy/TVRemoteIME)：本项目上游，感谢原作者的无私分享
- [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME)：触控板功能的来源，感谢贡献
