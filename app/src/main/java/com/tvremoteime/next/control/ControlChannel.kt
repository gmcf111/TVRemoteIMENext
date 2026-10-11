package com.tvremoteime.next.control

/**
 * 控制通道接口（02 文档 2.2 接口设计）
 *
 * 四通道：Root / IME / Accessibility / ADB
 * 能力域路由见 ChannelRouter；通道实现保持各自探测逻辑独立、可单测。
 */
interface ControlChannel {
    /** 通道标识："root" / "ime" / "a11y" / "adb" */
    val id: String

    /** 本通道可提供的能力集合 */
    val capabilities: Set<Capability>

    /** 轻量探测可用性（root 探测结果缓存，避免每次 su 卡顿） */
    fun isAvailable(): Boolean
}

/** 控制能力域（02 文档） */
enum class Capability {
    /** 按键 */
    KEY,

    /** 文字输入 */
    TEXT,

    /** 触控移动/点击 */
    TOUCH,

    /** 滑动手势 */
    SWIPE,

    /** 滚轮 */
    SCROLL,

    /** 截屏回传 */
    SCREENSHOT,

    /** 静默安装/卸载 */
    SILENT_INSTALL,

    /** 设置默认输入法 */
    SET_DEFAULT_IME
}
