# TVRemoteIMENext（小盒精灵Next）

[![build](https://github.com/gmcf111/TVRemoteIMENext/actions/workflows/build.yml/badge.svg)](https://github.com/gmcf111/TVRemoteIMENext/actions/workflows/build.yml)

通过浏览器遥控电视盒子的安卓应用：输入法（IME）+ HTTP 控制服务 + 触控板 + DLNA 投屏 + 边下边播（迅雷 P2SP/BT/ED2K）+ 可选 Root 增强。**Kotlin 重写版 v2.0.0**。

- 上游：[kingthy/TVRemoteIME](https://gitee.com/kingthy/TVRemoteIME) v1.4.1（GPL v2），融合 [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME) 的触控板功能
- 许可证：**GPL v2**（衍生作品，随源分发）

## 功能特性

| 功能 | 说明 |
|---|---|
| 远程输入/遥控 | 手机浏览器发文字/按键（方向键、HOME、返回、菜单、音量），`http://盒子IP:9978` |
| 触控板 | 移动光标、点击、长按、滑动、滚轮（辅助功能 API 24+，老系统自动回退 ADB/Root） |
| 文件管理 | 浏览/上传/下载/复制/剪切/删除（全盘授权后可访问整个存储） |
| 应用管理 | 列表/启动/卸载/安装（Root 可用时可静默安装） |
| 视频播放 | http/ftp/ed2k/迅雷链/种子边下边播（ijkplayer），可调系统播放器 |
| 电视截屏 | Root 开启后浏览器实时查看电视画面（手动/2 秒定时刷新） |
| DLNA 投屏 | 手机视频投到盒子播放，投屏名称可自定义 |
| 电视直播 | 自备 tv.txt 直播源播放 |
| 主题色 | 10 预设色 + 自定义 HEX，Android 界面 / Web 控制端同步 |
| 双语 | 中文 / English（Android 与 Web 端均可切换） |
| 自更新 | 从本仓库 Releases 检查新版本 |

## 技术要点

| 项 | 值 |
|---|---|
| 语言 | Kotlin（业务层 100%），thunder JNI 参数类保持 Java |
| minSdk / targetSdk | 19（Android 4.4）/ 35 |
| ABI | **仅 armeabi-v7a**（32 位盒子生态） |
| UI | Material Design 3（View 体系），主题色自定义 |
| 构建 | Gradle 8.13 / AGP 8.13.2 / JDK 17 |

## 四通道控制架构

控制指令按能力域自动选择最优通道，降级透明：

```
按键/文字：Root → 输入法 → ADB
触控/手势：辅助功能(7.0+) → Root → ADB
截屏/静默安装/设默认输入法：Root（不可用时 Web 端自动隐藏入口）
```

## 构建

```bash
./gradlew assembleDebug      # debug APK（app/build/outputs/apk/debug/）
./gradlew lint
```

## 仓库结构

```
app/          主应用：IME、HTTP server、四通道控制、触控板、DLNA、UI、主题
adb-lib/      ADB 协议库
ijkplayer/    ijkplayer 播放封装（so 原样保留）
thunder/      迅雷下载封装（Java + so 原样保留）
DroidDLNA/    DLNA 投屏栈（原样保留）
```

## 说明

- 控制端无访问密码，仅限可信局域网使用
- 直播源 tv.txt 默认置空，路径：`/Android/data/com.tvremoteime.next/files/tv.txt`
- 本项目仅 32 位（v7a）：纯 64 位新机型无法安装，非目标设备
- Root 增强默认关闭，在盒子端应用主页开关
