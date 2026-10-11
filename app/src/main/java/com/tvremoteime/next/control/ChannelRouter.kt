package com.tvremoteime.next.control

/**
 * 通道路由器（02 文档 2.1/2.2）
 *
 * 按能力域选优先级链，逐级探测可用性，降级对调用方透明。
 *
 * 按键/文字域：  Root → IME → ADB（文字特例：IME 激活时优先）
 * 触控/手势域：  Accessibility(24+) → Root → ADB
 * 截屏/安装/系统域：Root 独占（不可用时调用方隐藏入口并提示）
 */
class ChannelRouter(private val channels: List<ControlChannel>) {

    private val byId: Map<String, ControlChannel> = channels.associateBy { it.id }

    fun channel(id: String): ControlChannel? = byId[id]

    /** 按能力域择优：返回第一个可用的通道，全部不可用返回 null */
    fun pick(capability: Capability, prefer: String? = null): ControlChannel? {
        prefer?.let { p ->
            byId[p]?.takeIf { it.isAvailable() && capability in it.capabilities }?.let { return it }
        }
        val chain = priorityChain(capability)
        for (id in chain) {
            val ch = byId[id] ?: continue
            if (capability in ch.capabilities && ch.isAvailable()) return ch
        }
        return null
    }

    /** 各能力域当前激活通道（供 /channel-info 上报） */
    fun activeChannels(): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        for (cap in Capability.values()) {
            result[cap.name] = pick(cap)?.id
        }
        return result
    }

    companion object {
        /** 能力域 → 通道优先级链 */
        fun priorityChain(capability: Capability): List<String> = when (capability) {
            Capability.KEY -> listOf("root", "ime", "adb")
            Capability.TEXT -> listOf("ime", "root", "adb")
            Capability.TOUCH, Capability.SWIPE, Capability.SCROLL ->
                listOf("a11y", "root", "adb")
            Capability.SCREENSHOT, Capability.SILENT_INSTALL, Capability.SET_DEFAULT_IME ->
                listOf("root")
        }
    }
}
