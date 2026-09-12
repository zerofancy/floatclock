package top.ntutn.floatclock

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Single-instance guard based on [FileLock].
 *
 * Usage (typically at the very beginning of `main`):
 * ```kotlin
 * val lock = SingleInstanceChecker.acquire()
 * if (lock == null) {
 *     // Another instance is already running – exit.
 *     println("FloatClock is already running. Exiting.")
 *     return
 * }
 * // ... normal startup ...
 * // Release when shutting down (or let the OS clean up on exit).
 * lock.release()
 * ```
 */
object SingleInstanceChecker {
    private const val LOCK_FILE_NAME = ".floatclock.lock"

    /**
     * Try to acquire the application-wide file lock.
     *
     * @return A [SingleInstanceLock] handle if the lock was acquired (caller owns this instance),
     *         or `null` if another instance already holds the lock.
     */
    fun acquire(): SingleInstanceLock? {
        val lockFile = File(System.getProperty("java.io.tmpdir"), LOCK_FILE_NAME)
        return try {
            val channel = RandomAccessFile(lockFile, "rw").channel
            val lock = channel.tryLock()
            if (lock != null) {
                SingleInstanceLock(lockFile, channel, lock)
            } else {
                channel.close()
                null
            }
        } catch (e: Exception) {
            System.err.println("[FloatClock] Failed to acquire instance lock: ${e.message}")
            null
        }
    }
}

/**
 * Holds the resources for the file lock. Call [release] during graceful shutdown,
 * or simply let the JVM exit and the OS will clean up.
 */
class SingleInstanceLock(
    private val lockFile: File,
    private val channel: FileChannel,
    private val lock: FileLock,
) {
    fun release() {
        runCatching { lock.release() }
        runCatching { channel.close() }
        runCatching { lockFile.delete() }
    }
}
