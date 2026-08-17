package top.ntutn.floatclock.macos

import java.nio.file.Files
import java.nio.file.StandardCopyOption

object MacOSWindowBridge {
    private const val LIBRARY_RESOURCE = "/native/macos/libfloatclock_macos.dylib"

    private val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    private var libraryLoaded = false

    fun applyOverlayBehavior(windowTitle: String): Boolean {
        if (!isMacOS || !loadLibrary()) {
            return false
        }
        return runCatching { configureWindow(windowTitle) }
            .onFailure { System.err.println("Unable to configure the macOS overlay window: ${it.message}") }
            .getOrDefault(false)
    }

    @Synchronized
    private fun loadLibrary(): Boolean {
        if (libraryLoaded) {
            return true
        }

        return runCatching {
            val libraryStream = MacOSWindowBridge::class.java.getResourceAsStream(LIBRARY_RESOURCE)
                ?: error("Native library resource not found: $LIBRARY_RESOURCE")
            val nativeDirectory = Files.createTempDirectory("floatclock-native-")
            val nativeLibrary = nativeDirectory.resolve("libfloatclock_macos.dylib")
            libraryStream.use {
                Files.copy(it, nativeLibrary, StandardCopyOption.REPLACE_EXISTING)
            }
            nativeDirectory.toFile().deleteOnExit()
            nativeLibrary.toFile().deleteOnExit()
            System.load(nativeLibrary.toAbsolutePath().toString())
            libraryLoaded = true
            true
        }.onFailure {
            System.err.println("Unable to load the macOS window bridge: ${it.message}")
        }.getOrDefault(false)
    }

    private external fun configureWindow(windowTitle: String): Boolean
}
