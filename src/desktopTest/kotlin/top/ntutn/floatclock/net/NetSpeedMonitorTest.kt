package top.ntutn.floatclock.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetSpeedMonitorTest {
    private val second = 1_000_000_000L

    @Test
    fun firstSampleEstablishesBaselineAndActualElapsedTimeSetsRate() {
        val calculator = TrafficRateCalculator()
        assertNull(calculator.sample(mapOf("wifi" to TrafficCounters(100, 200)), 0))

        assertEquals(
            NetSpeed(1_000, 2_000),
            calculator.sample(mapOf("wifi" to TrafficCounters(350, 700)), second / 4),
        )
        assertEquals(
            NetSpeed(100, 200),
            calculator.sample(mapOf("wifi" to TrafficCounters(650, 1_300)), second * 3 + second / 4),
        )
    }

    @Test
    fun byteCountersRemainContinuousAcrossFourGiB() {
        val calculator = TrafficRateCalculator()
        val fourGiB = 1L shl 32
        assertNull(
            calculator.sample(
                mapOf("route" to TrafficCounters(fourGiB - 100, fourGiB - 250)),
                second,
            ),
        )

        assertEquals(
            NetSpeed(2_000, 4_000),
            calculator.sample(
                mapOf("route" to TrafficCounters(fourGiB + 900, fourGiB + 1_750)),
                second + second / 2,
            ),
        )
    }

    @Test
    fun disappearingSourceDoesNotCancelTrafficOnRemainingSource() {
        val calculator = TrafficRateCalculator()
        calculator.sample(
            mapOf(
                "wifi" to TrafficCounters(1_000, 2_000),
                "ethernet" to TrafficCounters(50_000_000, 80_000_000),
            ),
            second,
        )

        assertEquals(
            NetSpeed(500, 800),
            calculator.sample(mapOf("wifi" to TrafficCounters(1_500, 2_800)), second * 2),
        )
    }

    @Test
    fun newlyAppearingSourceDoesNotReportItsLifetimeBytesAsSpeed() {
        val calculator = TrafficRateCalculator()
        val lifetimeBytes = 30_000_000_000L
        calculator.sample(mapOf("wifi" to TrafficCounters(1_000, 2_000)), second)

        assertEquals(
            NetSpeed(500, 800),
            calculator.sample(
                mapOf(
                    "wifi" to TrafficCounters(1_500, 2_800),
                    "ethernet" to TrafficCounters(lifetimeBytes, lifetimeBytes),
                ),
                second * 2,
            ),
        )
        assertEquals(
            NetSpeed(1_300, 1_200),
            calculator.sample(
                mapOf(
                    "wifi" to TrafficCounters(2_000, 3_600),
                    "ethernet" to TrafficCounters(lifetimeBytes + 800, lifetimeBytes + 400),
                ),
                second * 3,
            ),
        )
    }

    @Test
    fun counterResetOnlyDropsThatDirectionOfThatSource() {
        val calculator = TrafficRateCalculator()
        calculator.sample(
            mapOf(
                "wifi" to TrafficCounters(5_000, 8_000),
                "ethernet" to TrafficCounters(1_000, 2_000),
            ),
            second,
        )

        assertEquals(
            NetSpeed(300, 1_000),
            calculator.sample(
                mapOf(
                    "wifi" to TrafficCounters(100, 8_500),
                    "ethernet" to TrafficCounters(1_300, 2_500),
                ),
                second * 2,
            ),
        )
        assertEquals(
            NetSpeed(600, 500),
            calculator.sample(
                mapOf(
                    "wifi" to TrafficCounters(400, 9_000),
                    "ethernet" to TrafficCounters(1_600, 100),
                ),
                second * 3,
            ),
        )
    }

    @Test
    fun validEmptySnapshotsProduceIdleSpeedAndClearRemovedBaselines() {
        val calculator = TrafficRateCalculator()
        assertNull(calculator.sample(emptyMap(), 0))
        assertEquals(NetSpeed(0, 0), calculator.sample(emptyMap(), second))
        assertEquals(
            NetSpeed(0, 0),
            calculator.sample(mapOf("wifi" to TrafficCounters(9_000, 7_000)), second * 2),
        )
        assertEquals(NetSpeed(0, 0), calculator.sample(emptyMap(), second * 3))
        assertEquals(
            NetSpeed(0, 0),
            calculator.sample(mapOf("wifi" to TrafficCounters(90_000, 70_000)), second * 4),
        )
        assertEquals(
            NetSpeed(300, 200),
            calculator.sample(mapOf("wifi" to TrafficCounters(90_300, 70_200)), second * 5),
        )
    }

    @Test
    fun resetDiscardsLifetimeBytesAndTimingFromPreviousSession() {
        val calculator = TrafficRateCalculator()
        calculator.sample(mapOf("wifi" to TrafficCounters(1_000, 2_000)), second)
        calculator.sample(mapOf("wifi" to TrafficCounters(2_000, 3_000)), second * 2)
        calculator.reset()

        assertNull(
            calculator.sample(mapOf("wifi" to TrafficCounters(50_000, 80_000)), second * 4),
        )
        assertEquals(
            NetSpeed(1_000, 2_000),
            calculator.sample(mapOf("wifi" to TrafficCounters(50_500, 81_000)), second * 4 + second / 2),
        )
    }

    @Test
    fun nonIncreasingSampleTimeRebaselinesInsteadOfInventingSpeed() {
        val calculator = TrafficRateCalculator()
        calculator.sample(mapOf("wifi" to TrafficCounters(1_000, 2_000)), second)

        assertNull(calculator.sample(mapOf("wifi" to TrafficCounters(1_200, 2_500)), second))
        assertEquals(
            NetSpeed(300, 500),
            calculator.sample(mapOf("wifi" to TrafficCounters(1_500, 3_000)), second * 2),
        )
        assertNull(
            calculator.sample(mapOf("wifi" to TrafficCounters(1_600, 3_200)), second / 2),
        )
        assertEquals(
            NetSpeed(300, 500),
            calculator.sample(mapOf("wifi" to TrafficCounters(1_900, 3_700)), second + second / 2),
        )
    }

    @Test
    fun reopeningWaitsUntilCancelledSessionHasClosedItsSource() = runBlocking {
        val firstSampled = CompletableDeferred<Unit>()
        val firstClosing = CompletableDeferred<Unit>()
        val secondSampled = CompletableDeferred<Unit>()
        val allowFirstClose = CountDownLatch(1)
        val opened = AtomicInteger()
        val active = AtomicInteger()
        val closed = AtomicInteger()
        val monitor = NetSpeedMonitor(
            intervalMs = 1_000,
            sourceFactory = {
                val session = opened.incrementAndGet()
                assertEquals(0, active.getAndIncrement(), "A new source opened before the old source closed")
                object : TrafficCounterSource {
                    override fun sample(): Map<String, TrafficCounters> {
                        if (session == 1) firstSampled.complete(Unit) else secondSampled.complete(Unit)
                        return emptyMap()
                    }

                    override fun close() {
                        if (session == 1) {
                            firstClosing.complete(Unit)
                            allowFirstClose.await()
                        }
                        active.decrementAndGet()
                        closed.incrementAndGet()
                    }
                }
            },
            nanoTime = System::nanoTime,
        )
        val firstJob = launch { monitor.run() }
        var secondJob: Job? = null
        try {
            withTimeout(5_000) {
                firstSampled.await()
                firstJob.cancel()
                firstClosing.await()
                // Starting in IO undispatched reaches run's mutex before launch returns.
                secondJob = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { monitor.run() }
                assertEquals(1, opened.get())
                allowFirstClose.countDown()
                firstJob.join()
                secondSampled.await()
                assertEquals(2, opened.get())
                secondJob!!.cancelAndJoin()
                assertEquals(0, active.get())
                assertEquals(2, closed.get())
                assertNull(monitor.speed.value)
            }
        } finally {
            firstJob.cancel()
            secondJob?.cancel()
            allowFirstClose.countDown()
            withContext(NonCancellable) {
                firstJob.join()
                secondJob?.join()
            }
        }
    }

    @Test
    fun failedSampleShowsUnavailableAndRecoveryStartsWithANewBaseline() = runBlocking {
        val source = ControlledSource()
        val clock = AtomicLong()
        val monitor = NetSpeedMonitor(1, { source }) { clock.getAndAdd(second) }
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                source.answer { mapOf("wifi" to TrafficCounters(1_000, 2_000)) }
                source.answer { mapOf("wifi" to TrafficCounters(1_100, 2_200)) }
                monitor.speed.first { it == NetSpeed(100, 200) }

                source.answer { throw IOException("Simulated sampling failure") }
                monitor.speed.first { it == null }
                source.answer { mapOf("wifi" to TrafficCounters(1_000_000_000, 2_000_000_000)) }
                // Awaiting the next request also confirms that the recovery baseline was processed.
                source.answer {
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(1_000_000_700, 2_000_000_900))
                }
                monitor.speed.first { it == NetSpeed(700, 900) }
            }
        } finally {
            job.cancel()
            source.cancelPendingSample()
            withContext(NonCancellable) { job.join() }
        }
        assertTrue(source.closed)
        assertNull(monitor.speed.value)
    }

    @Test
    fun initializationFailureRetriesAndRecoversWithoutReopening() = runBlocking {
        val attempts = AtomicInteger()
        val clock = AtomicLong()
        val source = ControlledSource()
        val monitor = NetSpeedMonitor(
            intervalMs = 1,
            sourceFactory = {
                if (attempts.incrementAndGet() == 1) throw IOException("Simulated initialization failure")
                source
            },
            nanoTime = { clock.getAndAdd(second) },
        )
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                source.answer {
                    assertEquals(2, attempts.get())
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(100, 200))
                }
                source.answer { mapOf("wifi" to TrafficCounters(500, 400)) }
                monitor.speed.first { it == NetSpeed(400, 200) }
                assertEquals(2, attempts.get())
            }
        } finally {
            job.cancel()
            source.cancelPendingSample()
            withContext(NonCancellable) { job.join() }
        }
        assertTrue(source.closed)
        assertNull(monitor.speed.value)
    }

    @Test
    fun consecutiveFailuresRecreateSourceAndRecoveryDoesNotReportLifetimeBytes() = runBlocking {
        val attempts = AtomicInteger()
        val active = AtomicInteger()
        val clock = AtomicLong()
        val original = ControlledSource { active.decrementAndGet() }
        val replacement = ControlledSource { active.decrementAndGet() }
        val monitor = NetSpeedMonitor(
            intervalMs = 1,
            sourceFactory = {
                val source = when (attempts.incrementAndGet()) {
                    1 -> original
                    2 -> replacement
                    else -> throw AssertionError("The replacement source should remain usable")
                }
                assertEquals(0, active.getAndIncrement(), "Sources must not overlap during recovery")
                source
            },
            nanoTime = { clock.getAndAdd(second) },
        )
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                original.answer { mapOf("wifi" to TrafficCounters(1_000, 2_000)) }
                original.answer { mapOf("wifi" to TrafficCounters(1_100, 2_200)) }
                monitor.speed.first { it == NetSpeed(100, 200) }

                repeat(3) { original.answer { throw IOException("Persistent sampling failure") } }
                replacement.answer {
                    assertTrue(original.closed)
                    assertEquals(1, original.closeCalls.get())
                    assertEquals(2, attempts.get())
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(40_000_000_000, 50_000_000_000))
                }
                replacement.answer {
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(40_000_000_700, 50_000_000_900))
                }
                monitor.speed.first { it == NetSpeed(700, 900) }
            }
        } finally {
            job.cancel()
            original.cancelPendingSample()
            replacement.cancelPendingSample()
            withContext(NonCancellable) { job.join() }
        }
        assertEquals(0, active.get())
        assertEquals(1, original.closeCalls.get())
        assertEquals(1, replacement.closeCalls.get())
        assertNull(monitor.speed.value)
    }

    @Test
    fun availableSpeedResetsFailureBudgetOnExistingSource() = runBlocking {
        val attempts = AtomicInteger()
        val clock = AtomicLong()
        val source = ControlledSource()
        val monitor = NetSpeedMonitor(
            intervalMs = 1,
            sourceFactory = {
                assertEquals(1, attempts.incrementAndGet(), "Intermittent failures must not recreate a working source")
                source
            },
            nanoTime = { clock.getAndAdd(second) },
        )
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                repeat(2) { source.answer { throw IOException("Temporary sampling failure") } }
                source.answer { mapOf("wifi" to TrafficCounters(1_000, 2_000)) }
                source.answer { mapOf("wifi" to TrafficCounters(1_100, 2_200)) }
                monitor.speed.first { it == NetSpeed(100, 200) }
                repeat(2) { source.answer { throw IOException("Another temporary sampling failure") } }
                source.answer {
                    assertFalse(source.closed)
                    assertEquals(1, attempts.get())
                    mapOf("wifi" to TrafficCounters(1_000_000, 2_000_000))
                }
                source.answer {
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(1_000_500, 2_000_600))
                }
                monitor.speed.first { it == NetSpeed(500, 600) }
            }
        } finally {
            job.cancel()
            source.cancelPendingSample()
            withContext(NonCancellable) { job.join() }
        }
        assertEquals(1, attempts.get())
        assertEquals(1, source.closeCalls.get())
        assertNull(monitor.speed.value)
    }

    @Test
    fun failuresAlternatingWithBaselinesStillRecreateAnUnavailableSource() = runBlocking {
        val attempts = AtomicInteger()
        val clock = AtomicLong()
        val original = ControlledSource()
        val replacement = ControlledSource()
        val monitor = NetSpeedMonitor(
            intervalMs = 1,
            sourceFactory = {
                when (attempts.incrementAndGet()) {
                    1 -> original
                    2 -> replacement
                    else -> throw AssertionError("The replacement source should remain usable")
                }
            },
            nanoTime = { clock.getAndAdd(second) },
        )
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                original.answer { throw IOException("First sampling failure") }
                original.answer { mapOf("wifi" to TrafficCounters(1_000, 2_000)) }
                original.answer { throw IOException("Second sampling failure") }
                original.answer { mapOf("wifi" to TrafficCounters(100_000, 200_000)) }
                original.answer { throw IOException("Third sampling failure without usable speed") }

                replacement.answer {
                    assertTrue(original.closed)
                    assertEquals(2, attempts.get())
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(40_000_000_000, 50_000_000_000))
                }
                replacement.answer {
                    assertNull(monitor.speed.value)
                    mapOf("wifi" to TrafficCounters(40_000_000_500, 50_000_000_600))
                }
                monitor.speed.first { it == NetSpeed(500, 600) }
            }
        } finally {
            job.cancel()
            original.cancelPendingSample()
            replacement.cancelPendingSample()
            withContext(NonCancellable) { job.join() }
        }
        assertEquals(1, original.closeCalls.get())
        assertEquals(1, replacement.closeCalls.get())
        assertNull(monitor.speed.value)
    }

    @Test
    fun cancellingWhileFailedSourceClosesDoesNotOpenReplacementOrCloseTwice() = runBlocking {
        val closing = CompletableDeferred<Unit>()
        val allowClose = CountDownLatch(1)
        val attempts = AtomicInteger()
        val active = AtomicInteger()
        val original = ControlledSource {
            closing.complete(Unit)
            allowClose.await()
            active.decrementAndGet()
        }
        val replacement = ControlledSource { active.decrementAndGet() }
        val monitor = NetSpeedMonitor(
            intervalMs = 1,
            sourceFactory = {
                val source = if (attempts.incrementAndGet() == 1) original else replacement
                assertEquals(0, active.getAndIncrement())
                source
            },
            nanoTime = System::nanoTime,
        )
        val job = launch { monitor.run() }
        try {
            withTimeout(5_000) {
                repeat(3) { original.answer { throw IOException("Persistent sampling failure") } }
                closing.await()
                assertEquals(1, attempts.get())
                job.cancel()
                allowClose.countDown()
                job.join()
                assertEquals(1, attempts.get())
                assertEquals(1, original.closeCalls.get())
                assertEquals(0, replacement.closeCalls.get())
                assertEquals(0, active.get())
                assertNull(monitor.speed.value)
            }
        } finally {
            job.cancel()
            original.cancelPendingSample()
            replacement.cancelPendingSample()
            allowClose.countDown()
            withContext(NonCancellable) { job.join() }
        }
    }

    private class ControlledSource(private val onClose: () -> Unit = {}) : TrafficCounterSource {
        private val requests = Channel<Unit>(Channel.UNLIMITED)
        private val answers = LinkedBlockingQueue<() -> Map<String, TrafficCounters>>()
        val closeCalls = AtomicInteger()

        @Volatile
        var closed = false
            private set

        override fun sample(): Map<String, TrafficCounters> {
            requests.trySend(Unit)
            return answers.take().invoke()
        }

        suspend fun answer(value: () -> Map<String, TrafficCounters>) {
            requests.receive()
            answers.put(value)
        }

        fun cancelPendingSample() {
            answers.offer { throw CancellationException("Test session cancelled") }
        }

        override fun close() {
            onClose()
            closed = true
            closeCalls.incrementAndGet()
        }
    }
}
