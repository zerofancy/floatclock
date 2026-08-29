package top.ntutn.floatclock

import top.ntutn.floatclock.macos.MacOSWindowBridge
import top.ntutn.floatclock.windows.WindowsAutoStart

/**
 * Cross-platform auto-start (launch-at-login) facade.
 * Supports macOS (LaunchAgent) and Windows (HKCU Run registry key).
 * Linux is not implemented yet.
 */
object AutoStart {
    private val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    /** Whether the current platform supports toggling autostart. */
    fun isSupported(): Boolean = isMacOS || isWindows

    /** Whether autostart is currently enabled; returns false on unsupported platforms. */
    fun isEnabled(): Boolean = when {
        isMacOS -> MacOSWindowBridge.isLoginItemEnabled()
        isWindows -> WindowsAutoStart.isLoginItemEnabled()
        else -> false
    }

    /**
     * Enable or disable autostart.
     * @return true on success; false on failure or unsupported platforms.
     */
    fun setEnabled(enabled: Boolean): Boolean = when {
        isMacOS -> MacOSWindowBridge.setLoginItemEnabled(enabled)
        isWindows -> WindowsAutoStart.setLoginItemEnabled(enabled)
        else -> false
    }
}
