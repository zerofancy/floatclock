package top.ntutn.floatclock.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import oshi.SystemInfo
import oshi.hardware.NetworkIF

data class NetSpeed(val downBytesPerSec: Long, val upBytesPerSec: Long)

class NetSpeedMonitor(private val intervalMs: Long = 1_000L) {
    private val hal = SystemInfo().hardware
    private var prevRecv = 0L
    private var prevSent = 0L
    private var prevTs = 0L

    private val _speed = MutableStateFlow(NetSpeed(0, 0))
    val speed: StateFlow<NetSpeed> = _speed
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sampleOnce()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private fun sampleOnce() {
        var totalRecv = 0L
        var totalSent = 0L
        for (nif in hal.networkIFs) {
            nif.updateAttributes()
            if (!nif.isValidCandidate()) continue
            totalRecv += nif.bytesRecv
            totalSent += nif.bytesSent
        }
        val now = System.currentTimeMillis()
        if (prevTs > 0) {
            val dt = (now - prevTs).coerceAtLeast(1)
            val down = ((totalRecv - prevRecv) * 1000.0 / dt).toLong().coerceAtLeast(0)
            val up = ((totalSent - prevSent) * 1000.0 / dt).toLong().coerceAtLeast(0)
            _speed.value = NetSpeed(down, up)
        }
        prevRecv = totalRecv
        prevSent = totalSent
        prevTs = now
    }

    private fun NetworkIF.isValidCandidate(): Boolean {
        if (runCatching { queryNetworkInterface().isLoopback }.getOrDefault(false)) return false
        // macOS/部分 Linux 驱动无法拿到 ifOperStatus，通常返回 UNKNOWN；
        // 这里只排除明确 DOWN/NOT_PRESENT/LOWER_LAYER_DOWN，保留 UP/UNKNOWN/DORMANT/TESTING 等。
        when (ifOperStatus) {
            NetworkIF.IfOperStatus.DOWN,
            NetworkIF.IfOperStatus.NOT_PRESENT,
            NetworkIF.IfOperStatus.LOWER_LAYER_DOWN -> return false
            else -> Unit
        }
        // 排除链路速率为 0 的接口（例如 WAN Miniport 这类"Up"但毫无实际流量的伪网卡）。
        // 用 > 0 而不是 > 0L 避免类型歧义；对于上报速率异常大的虚拟网卡（如 Meta Tunnel 100Gbps），
        // 这里不做速率上限判断，留给下面的名称/特征过滤。
        if (runCatching { speed }.getOrDefault(0L) <= 0L && bytesRecv <= 0L && bytesSent <= 0L) {
            return false
        }
        val name = "${displayName.orEmpty()} ${name.orEmpty()}"
        // Windows 虚拟网卡 + macOS 常见虚拟/调试接口（awdl/llw/utun 等）
        val vmKw = listOf(
            // 虚拟机 / 容器 / Hyper-V
            "VMware", "VirtualBox", "vEthernet", "vSwitch", "Hyper-V", "Container",
            // 回环 / 抓包 / TAP-TUN
            "Loopback", "TAP", "TUN", "Npcap",
            // 蓝牙 / 直连虚拟适配器
            "Bluetooth", "Wi-Fi Direct", "Virtual Adapter",
            // 隧道 / VPN / 运营商级虚拟接口
            "Tunnel", "Meta", "WAN Miniport", "Teredo", "6to4", "IP-HTTPS", "PPPOE",
            "PPTP", "L2TP", "IKEv2", "SSTP", "Tunneling", "Pseudo-Interface",
            // macOS 虚拟/调试接口
            "awdl", "llw", "utun", "feth", "bridge", "vlan", "ap1", "p2p", "gif", "stf",
            // Windows 内核调试
            "Kernel Debug",
        )
        if (vmKw.any { name.contains(it, ignoreCase = true) }) return false

        return true
    }
}

fun Long.humanBps(): String = when {
    this >= 1024L * 1024 -> "%.1f MB/s".format(this / 1024.0 / 1024.0)
    this >= 1024L        -> "%.1f KB/s".format(this / 1024.0)
    else                 -> "$this B/s"
}
