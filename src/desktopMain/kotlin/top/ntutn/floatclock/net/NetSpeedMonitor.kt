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
        val name = "${displayName.orEmpty()} ${name.orEmpty()}"
        // Windows 虚拟网卡 + macOS 常见虚拟/调试接口（awdl/llw/utun 等）
        val vmKw = listOf(
            "VMware", "VirtualBox", "vEthernet", "Loopback", "TAP", "Bluetooth", "Hyper-V",
            "awdl", "llw", "utun", "feth", "bridge", "vlan", "ap1", "p2p", "gif", "stf",
        )
        return vmKw.none { name.contains(it, ignoreCase = true) }
    }
}

fun Long.humanBps(): String = when {
    this >= 1024L * 1024 -> "%.1f MB/s".format(this / 1024.0 / 1024.0)
    this >= 1024L        -> "%.1f KB/s".format(this / 1024.0)
    else                 -> "$this B/s"
}
