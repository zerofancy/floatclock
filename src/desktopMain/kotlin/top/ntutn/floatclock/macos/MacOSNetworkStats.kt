package top.ntutn.floatclock.macos

import top.ntutn.floatclock.net.TrafficCounterSource
import top.ntutn.floatclock.net.TrafficCounters

internal class MacOSNetworkStats : TrafficCounterSource {
    private var handle: Long

    init {
        check(MacOSWindowBridge.loadLibrary()) { "Unable to load the macOS network statistics bridge" }
        handle = createNative()
        check(handle != 0L) { "Unable to create the macOS network statistics manager" }
    }

    @Synchronized
    override fun sample(): Map<String, TrafficCounters> {
        check(handle != 0L) { "The macOS network statistics manager is closed" }
        val snapshot = sampleNative(handle)
        return buildMap(snapshot.size / 4) {
            for (offset in snapshot.indices step 4) {
                put(
                    "${snapshot[offset]}:${snapshot[offset + 1]}",
                    TrafficCounters(snapshot[offset + 2], snapshot[offset + 3]),
                )
            }
        }
    }

    @Synchronized
    override fun close() {
        if (handle == 0L) return
        closeNative(handle)
        handle = 0L
    }

    private external fun createNative(): Long
    private external fun sampleNative(handle: Long): LongArray
    private external fun closeNative(handle: Long)
}
