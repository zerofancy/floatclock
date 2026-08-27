package top.ntutn.floatclock.macos

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.Paths

object MacOSWindowBridge {
    private const val LIBRARY_RESOURCE = "/native/macos/libfloatclock_macos.dylib"
    private const val LAUNCH_AGENT_LABEL = "top.ntutn.floatclock"

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

    fun isLoginItemEnabled(): Boolean {
        if (!isMacOS) return false
        return runCatching { isLaunchAgentEnabled() }
            .onFailure { System.err.println("[FloatClock] Failed to check login item status: ${it.message}") }
            .getOrDefault(false)
    }

    fun setLoginItemEnabled(enabled: Boolean): Boolean {
        if (!isMacOS) return false
        return runCatching { setLaunchAgentEnabled(enabled) }
            .onFailure { System.err.println("[FloatClock] Failed to set login item: ${it.message}") }
            .getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // LaunchAgent implementation (reliable, works on all macOS versions)
    // ------------------------------------------------------------------

    private fun launchAgentPlistPath(): String =
        "${System.getProperty("user.home")}/Library/LaunchAgents/$LAUNCH_AGENT_LABEL.plist"

    /**
     * Locates the .app bundle by walking up from the running JAR.
     * For a jpackage/Compose Desktop app the JAR sits at Contents/app/<name>.jar,
     * so walking up two levels reaches the .app.
     */
    private fun findAppBundlePath(): String? {
        return runCatching {
            val codeSource = MacOSWindowBridge::class.java.protectionDomain.codeSource
            val jarUri = codeSource?.location?.toURI() ?: return@runCatching null
            var path = Paths.get(jarUri).parent
            while (path != null) {
                val name = path.fileName?.toString()
                if (name != null && name.endsWith(".app")) {
                    return@runCatching path.toAbsolutePath().toString()
                }
                path = path.parent
            }
            null
        }.onFailure {
            System.err.println("[FloatClock] Failed to determine .app bundle path: ${it.message}")
        }.getOrNull()
    }

    private fun currentUid(): String = runCatching {
        val process = ProcessBuilder("/usr/bin/id", "-u")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        output
    }.getOrDefault("501")

    private fun runLaunchCtl(vararg args: String): Boolean = runCatching {
        val cmd = arrayOf("/bin/launchctl", *args)
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        if (exit != 0) {
            System.err.println("[FloatClock] launchctl ${args.joinToString(" ")} failed (exit $exit): $output")
        }
        exit == 0
    }.onFailure {
        System.err.println("[FloatClock] launchctl ${args.joinToString(" ")} exception: ${it.message}")
    }.getOrDefault(false)

    private fun isLaunchAgentEnabled(): Boolean {
        val plistFile = java.io.File(launchAgentPlistPath())
        if (!plistFile.exists()) return false
        return runLaunchCtl("list", LAUNCH_AGENT_LABEL)
    }

    private fun setLaunchAgentEnabled(enabled: Boolean): Boolean {
        val plistPath = launchAgentPlistPath()
        val plistFile = java.io.File(plistPath)
        val uid = currentUid()

        if (enabled) {
            val appPath = findAppBundlePath()
            if (appPath == null) {
                System.err.println("[FloatClock] LaunchAgent: cannot determine .app bundle path")
                return false
            }
            System.err.println("[FloatClock] LaunchAgent: using app path: $appPath")

            // Write the plist.
            val plistContent = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$LAUNCH_AGENT_LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/bin/open</string>
        <string>-a</string>
        <string>$appPath</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
"""
            plistFile.parentFile?.mkdirs()
            plistFile.writeText(plistContent)
            System.err.println("[FloatClock] LaunchAgent: plist written to $plistPath")

            // If already loaded, bootout first so the new plist takes effect.
            if (isLaunchAgentEnabled()) {
                System.err.println("[FloatClock] LaunchAgent: already loaded, bootout first")
                runLaunchCtl("bootout", "gui/$uid/$LAUNCH_AGENT_LABEL")
            }

            // Bootstrap (load) the agent.
            val bootstrapped = runLaunchCtl("bootstrap", "gui/$uid", plistPath)
            if (!bootstrapped) {
                System.err.println("[FloatClock] LaunchAgent: bootstrap failed, cleaning up plist")
                plistFile.delete()
                return false
            }

            // Ensure it is enabled (auto-start on next login).
            runLaunchCtl("enable", "gui/$uid/$LAUNCH_AGENT_LABEL")
            System.err.println("[FloatClock] LaunchAgent: registered successfully")
            return true
        } else {
            if (isLaunchAgentEnabled()) {
                runLaunchCtl("bootout", "gui/$uid/$LAUNCH_AGENT_LABEL")
            }
            if (plistFile.exists()) {
                plistFile.delete()
            }
            System.err.println("[FloatClock] LaunchAgent: unregistered successfully")
            return true
        }
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
