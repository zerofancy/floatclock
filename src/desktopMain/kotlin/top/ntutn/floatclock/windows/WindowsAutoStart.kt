package top.ntutn.floatclock.windows

import java.nio.file.Paths

/**
 * Windows 用户级开机启动：通过注册表 HKCU Run 键实现。
 * 无管理员权限要求，对应 macOS LaunchAgent 的定位。
 */
object WindowsAutoStart {
    private const val REG_RUN_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val REG_VALUE_NAME = "floatclock"

    private val isWindows: Boolean get() =
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    fun isLoginItemEnabled(): Boolean {
        if (!isWindows) return false
        return runCatching {
            val result = runRegCmd("query", REG_RUN_PATH, "/v", REG_VALUE_NAME)
            result.exitCode == 0 && result.stdout.contains(REG_VALUE_NAME, ignoreCase = true)
        }.onFailure {
            System.err.println("[FloatClock][Windows] Failed to query autostart: ${it.message}")
        }.getOrDefault(false)
    }

    fun setLoginItemEnabled(enabled: Boolean): Boolean {
        if (!isWindows) return false
        return runCatching {
            if (enabled) {
                val exePath = findExecutablePath()
                if (exePath == null) {
                    System.err.println("[FloatClock][Windows] Cannot determine executable path; refuse to enable autostart")
                    return@runCatching false
                }
                System.err.println("[FloatClock][Windows] Will register autostart exe: $exePath")

                val addResult = runRegCmd(
                    "add", REG_RUN_PATH,
                    "/v", REG_VALUE_NAME,
                    "/t", "REG_SZ",
                    "/d", exePath,
                    "/f"
                )
                if (addResult.exitCode != 0) {
                    System.err.println("[FloatClock][Windows] reg add failed (exit ${addResult.exitCode}): ${addResult.stderr}")
                    return@runCatching false
                }

                // 二次确认
                val verified = isLoginItemEnabled()
                if (!verified) {
                    System.err.println("[FloatClock][Windows] reg add succeeded but value not present; rollback")
                    runRegCmd("delete", REG_RUN_PATH, "/v", REG_VALUE_NAME, "/f")
                    return@runCatching false
                }
                System.err.println("[FloatClock][Windows] Autostart registered successfully")
                true
            } else {
                val result = runRegCmd("delete", REG_RUN_PATH, "/v", REG_VALUE_NAME, "/f")
                if (result.exitCode != 0) {
                    // 退出码 1 且 stderr 含 "not found" 视作"本来就没有"，不报错
                    val notFound = result.stderr.contains("not find", ignoreCase = true)
                            || result.stderr.contains("not found", ignoreCase = true)
                    if (notFound) {
                        System.err.println("[FloatClock][Windows] Autostart value was not present; nothing to delete")
                        return@runCatching true
                    }
                    System.err.println("[FloatClock][Windows] reg delete failed (exit ${result.exitCode}): ${result.stderr}")
                    return@runCatching false
                }
                System.err.println("[FloatClock][Windows] Autostart unregistered successfully")
                true
            }
        }.onFailure {
            System.err.println("[FloatClock][Windows] Exception while toggling autostart: ${it.message}")
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------------
    // 可执行文件路径解析
    // ------------------------------------------------------------------

    /**
     * 定位 jpackage 生成的 floatclock.exe。
     *
     * jpackage Windows 典型布局：
     *   <installdir>/floatclock.exe          ← 启动 launcher（注册目标）
     *   <installdir>/app/floatclock.jar      ← 当前 JAR
     *   <installdir>/runtime/bin/java.exe    ← 当前 JVM 进程的 exe
     *
     * 顺序：
     *  1. 从 JAR 的父目录（app）向上找一层（installdir）下的 floatclock.exe
     *  2. 回退：从当前进程的 command()（runtime/bin/java.exe）向上找 installdir 下的 floatclock.exe
     *  3. 必须是 floatclock.exe（避免 IDE 调试时误注册 idea64.exe / java.exe）
     */
    private fun findExecutablePath(): String? {
        // 1) 从当前运行的 JAR 推导
        runCatching {
            val codeSource = WindowsAutoStart::class.java.protectionDomain.codeSource
            val jarUri = codeSource?.location?.toURI() ?: return@runCatching null
            val jarPath = Paths.get(jarUri)
            // jarPath = <installdir>/app/floatclock.jar → parent=app → parent=installdir
            val installDir = jarPath.parent?.parent ?: return@runCatching null
            val candidate = installDir.resolve("floatclock.exe")
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString()
            }
        }

        // 2) 从当前进程命令行推导
        runCatching {
            val cmd = ProcessHandle.current().info().command().orElse(null) ?: return@runCatching null
            val exe = Paths.get(cmd)
            // 可能是 runtime/bin/java.exe，向上找 2 层
            val candidate = exe.parent?.parent?.parent?.resolve("floatclock.exe")
                ?: return@runCatching null
            if (java.nio.file.Files.exists(candidate)) {
                return candidate.toAbsolutePath().toString()
            }
        }

        return null
    }

    // ------------------------------------------------------------------
    // reg.exe 命令封装
    // ------------------------------------------------------------------

    private data class RegResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runRegCmd(vararg args: String): RegResult = runCatching {
        val cmd = arrayOf("reg", *args)
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exit = process.waitFor()
        RegResult(exit, stdout, stderr)
    }.getOrElse {
        System.err.println("[FloatClock][Windows] reg.exe execution exception: ${it.message}")
        RegResult(-1, "", it.message ?: "unknown error")
    }
}
