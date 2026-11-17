package top.ntutn.floatclock.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import oshi.SystemInfo
import oshi.hardware.NetworkIF
import top.ntutn.floatclock.macos.MacOSNetworkStats

data class NetSpeed(val downBytesPerSec: Long, val upBytesPerSec: Long)

internal data class TrafficCounters(val received: Long, val sent: Long)

internal interface TrafficCounterSource : AutoCloseable {
    fun sample(): Map<String, TrafficCounters>
    override fun close() = Unit
}

internal class TrafficRateCalculator {
    private var previous: Map<String, TrafficCounters>? = null
    private var previousNanos = 0L

    fun reset() {
        previous = null
    }

    fun sample(counters: Map<String, TrafficCounters>, nowNanos: Long): NetSpeed? {
        val before = previous
        val elapsed = nowNanos - previousNanos
        previous = counters.toMap()
        previousNanos = nowNanos
        if (before == null || elapsed <= 0L) return null

        var received = 0L
        var sent = 0L
        for ((id, value) in counters) {
            val old = before[id] ?: continue
            received += (value.received - old.received).coerceAtLeast(0L)
            sent += (value.sent - old.sent).coerceAtLeast(0L)
        }
        return NetSpeed(
            (received * 1_000_000_000.0 / elapsed).toLong(),
            (sent * 1_000_000_000.0 / elapsed).toLong(),
        )
    }
}

class NetSpeedMonitor internal constructor(
    private val intervalMs: Long,
    private val sourceFactory: () -> TrafficCounterSource,
    private val nanoTime: () -> Long,
) {
    constructor(intervalMs: Long = 1_000L) : this(intervalMs, ::platformCounterSource, System::nanoTime)

    private val logger = LoggerFactory.getLogger("NetSpeedMonitor")
    private val runMutex = Mutex()
    private val _speed = MutableStateFlow<NetSpeed?>(null)
    val speed: StateFlow<NetSpeed?> = _speed

    /** One session owns the source until cancellation has finished closing it. */
    suspend fun run() = withContext(Dispatchers.IO) {
        runMutex.withLock {
            _speed.value = null
            val source = try {
                sourceFactory()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                logger.warn("Unable to initialize network speed sampling", failure)
                return@withLock
            }
            val calculator = TrafficRateCalculator()
            var unavailable = false
            try {
                while (currentCoroutineContext().isActive) {
                    try {
                        val counters = source.sample()
                        currentCoroutineContext().ensureActive()
                        _speed.value = calculator.sample(counters, nanoTime())
                        if (unavailable) logger.debug("Network speed sampling recovered")
                        unavailable = false
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        calculator.reset()
                        _speed.value = null
                        if (!unavailable) logger.warn("Network speed sampling is unavailable", failure)
                        unavailable = true
                    }
                    delay(intervalMs)
                }
            } finally {
                _speed.value = null
                try {
                    source.close()
                } catch (failure: Exception) {
                    logger.warn("Unable to close network speed sampling", failure)
                }
            }
        }
    }
}

private fun platformCounterSource(): TrafficCounterSource =
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) MacOSNetworkStats()
    else OshiTrafficCounterSource()

private class OshiTrafficCounterSource : TrafficCounterSource {
    private val hal = SystemInfo().hardware

    override fun sample(): Map<String, TrafficCounters> = buildMap {
        for (nif in hal.networkIFs) {
            val updated = nif.updateAttributes()
            if (!nif.isValidCandidate()) continue
            check(updated) { "Unable to update network interface ${nif.name}" }
            val id = "${nif.queryNetworkInterface().index}:${nif.name}"
            put(id, TrafficCounters(nif.bytesRecv, nif.bytesSent))
        }
    }

    private fun NetworkIF.isValidCandidate(): Boolean {
        if (runCatching { queryNetworkInterface().isLoopback }.getOrDefault(false)) return false
        // 部分 Linux 驱动无法拿到 ifOperStatus，通常返回 UNKNOWN；
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
