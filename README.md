# TVRemoteIMENext（小盒精灵Next）

通过浏览器遥控电视盒子的安卓应用：输入法（IME）+ HTTP 控制服务 + 触控板 + 视频播放 + DLNA 投屏 + 可选 Root 增强。**Kotlin 重写版 v3.0.0**。

- 上游：[kingthy/TVRemoteIME](https://gitee.com/kingthy/TVRemoteIME) v1.4.1（GPL v2），融合 [cacaview/TVRemoteIME](https://github.com/cacaview/TVRemoteIME) 的触控板功能
- 许可证：**GPL v2**（衍生作品，随源分发）

## v3.0 变更：移除过时的下载与直播功能

以下功能已被移除，这类需求在现代有大量更专业的替代软件：

- **迅雷边下边播**（thunder://、ed2k://、磁力、BT 种子）：迅雷 MiniThunder SDK 已于 2017 年停止更新，`thunder/` 模块连同其 4 个原生库整体移除
- **电视直播源播放**（tv.txt 频道列表）：IPTV 直播已有专门应用，`/tv.txt` 端点与 Web 端频道 UI 移除

保留的播放能力：本地媒体文件、http/https 直链、rtmp/rtsp/mms 直播流地址（在「视频播放」Tab 输入地址即可播放），可调用系统播放器。

## 功能特性

| 功能 | 说明 |
|---|---|
| 远程输入/遥控 | 手机浏览器发文字/按键（方向键、HOME、返回、菜单、音量），`http://盒子IP:9978` |
| 触控板 | 移动光标、点击、长按、滑动、滚轮（辅助功能 API 24+，老系统自动回退 ADB/Root） |
| 文件管理 | 浏览/上传/下载/复制/剪切/删除（全盘授权后可访问整个存储） |
| 应用管理 | 列表/启动/卸载/安装（Root 可用时可静默安装） |
| 视频播放 | 本地文件 / http(s) / rtmp / rtsp / mms 直链（ijkplayer），可调系统播放器 |
| 电视截屏 | Root 开启后浏览器实时查看电视画面（手动/2 秒定时刷新） |
| DLNA 投屏 | 手机视频投到盒子播放，投屏名称可自定义 |
| 主题色 | 10 预设色 + 自定义 HEX，Android 界面 / Web 控制端同步 |
| 双语 | 中文 / English（Android 与 Web 端均可切换） |
| 自更新 | 从本仓库 Releases 检查新版本 |

## 技术要点

| 项 | 值 |
|---|---|
| 语言 | app 主应用 Kotlin；ADB 协议、播放器及 DLNA 仍保留 Java 实现 |
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
ijkplayer/    ijkplayer 播放封装（so 原样保留；v3.0 已剥离迅雷下载层，仅保留直链播放）
DroidDLNA/    DLNA 投屏栈（原样保留）
```

## 说明

- HTTP/DLNA 使用独立前台服务；可从主界面手动启动，输入法/辅助功能启用后也会启动

- 控制端无访问密码，仅限可信局域网使用
- 本项目仅 32 位（v7a）：纯 64 位新机型无法安装，非目标设备
- Root 增强默认关闭，在盒子端应用主页开关
