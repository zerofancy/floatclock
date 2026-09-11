package top.ntutn.floatclock.linux

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Linux 用户级开机启动：通过 XDG autostart 机制实现。
 * 在 `~/.config/autostart/floatclock.desktop` 写入/删除 desktop 文件。
 * 兼容 GNOME、KDE、XFCE、MATE、Cinnamon 等主流桌面环境。
 */
object LinuxAutoStart {
    private const val DESKTOP_FILE_NAME = "floatclock.desktop"

    private val isLinux: Boolean get() =
        System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

    private fun autostartDir(): File {
        val xdgConfig = System.getenv("XDG_CONFIG_HOME")
            ?: "${System.getProperty("user.home")}/.config"
        return File(xdgConfig, "autostart")
    }

    private fun autostartFile(): File = File(autostartDir(), DESKTOP_FILE_NAME)

    fun isLoginItemEnabled(): Boolean {
        if (!isLinux) return false
        return runCatching {
            val file = autostartFile()
            file.exists() && file.readText().contains("Exec=")
        }.onFailure {
            System.err.println("[FloatClock][Linux] Failed to query autostart: ${it.message}")
        }.getOrDefault(false)
    }

    fun setLoginItemEnabled(enabled: Boolean): Boolean {
        if (!isLinux) return false
        return runCatching {
            if (enabled) {
                enableAutostart()
            } else {
                disableAutostart()
            }
        }.onFailure {
            System.err.println("[FloatClock][Linux] Exception while toggling autostart: ${it.message}")
        }.getOrDefault(false)
    }

    private fun enableAutostart(): Boolean {
        val execPath = findExecutablePath()
        if (execPath == null) {
            System.err.println("[FloatClock][Linux] Cannot determine executable path; refuse to enable autostart")
            return false
        }
        System.err.println("[FloatClock][Linux] Will register autostart exec: $execPath")

        val dir = autostartDir()
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val desktopContent = """
            |[Desktop Entry]
            |Type=Application
            |Name=简易桌面悬浮时钟
            |Comment=悬浮在所有窗口上方，更方便掌握摸鱼节奏。
            |Exec=$execPath
            |Icon=preferences-system-time
            |Terminal=false
            |X-GNOME-Autostart-enabled=true
            |""".trimMargin()

        val file = autostartFile()
        file.writeText(desktopContent)

        // 验证写入成功
        if (!isLoginItemEnabled()) {
            System.err.println("[FloatClock][Linux] Desktop file written but verification failed; cleaning up")
            file.delete()
            return false
        }

        System.err.println("[FloatClock][Linux] Autostart registered successfully: ${file.absolutePath}")
        return true
    }

    private fun disableAutostart(): Boolean {
        val file = autostartFile()
        if (file.exists()) {
            file.delete()
            System.err.println("[FloatClock][Linux] Autostart unregistered successfully")
        } else {
            System.err.println("[FloatClock][Linux] Autostart file was not present; nothing to delete")
        }
        return true
    }

    // ------------------------------------------------------------------
    // 可执行文件路径解析
    // ------------------------------------------------------------------

    /**
     * 定位 jpackage 生成的 floatclock 可执行文件。
     *
     * jpackage Linux 典型布局：
     *   <installdir>/bin/floatclock          ← 启动 launcher（注册目标）
     *   <installdir>/lib/app/floatclock.jar  ← 当前 JAR
     *   <installdir>/runtime/bin/java        ← 当前 JVM 进程的 exe
     *
     * 顺序：
     *  1. 从 JAR 的父目录（app）向上找两层（installdir）下的 bin/floatclock
     *  2. 回退：从当前进程的 command() 向上找 installdir 下的 bin/floatclock
     *  3. 优先匹配 floatclock（launcher），而非 java
     */
    private fun findExecutablePath(): String? {
        // 1) 从当前运行的 JAR 推导
        runCatching {
            val codeSource = LinuxAutoStart::class.java.protectionDomain.codeSource
            val jarUri = codeSource?.location?.toURI() ?: return@runCatching null
            val jarPath = Paths.get(jarUri)
            // jarPath = <installdir>/lib/app/floatclock.jar → parent=app → parent=lib → parent=installdir
            val installDir = jarPath.parent?.parent?.parent ?: return@runCatching null
            val candidate = installDir.resolve("bin").resolve("floatclock")
            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString()
            }
        }

        // 2) 从当前进程命令行推导
        runCatching {
            val cmd = ProcessHandle.current().info().command().orElse(null) ?: return@runCatching null
            val exe = Paths.get(cmd)
            // 可能是 runtime/bin/java，向上找 3 层到 installdir
            val candidate = exe.parent?.parent?.parent?.resolve("bin")?.resolve("floatclock")
                ?: return@runCatching null
            if (Files.exists(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString()
            }
        }

        return null
    }
}
