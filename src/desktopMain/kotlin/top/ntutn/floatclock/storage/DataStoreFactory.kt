package top.ntutn.floatclock.storage

import org.jetbrains.skiko.hostOs
import top.ntutn.floatclock.BuildConfig
import java.io.File
import kotlin.apply

class DataStoreFactory {
    fun createThemeDataStore(): ThemeDataStore {
        return ThemeDataStore {
            val configFile = getPlatformConfigFile()
            configFile.absolutePath
        }
    }

    private fun getPlatformConfigFile(): File {
        val configDir = when {
            hostOs.isWindows -> getWindowsConfigDir()
            hostOs.isMacOS -> getMacConfigDir()
            hostOs.isLinux -> getLinuxConfigDir()
            else -> getFallbackConfigDir()
        }.apply { mkdirs() }

        return File(configDir, "theme.json")
    }

    private fun getWindowsConfigDir(): File {
        return File(
            System.getenv("APPDATA") ?: System.getProperty("user.home"),
            BuildConfig.APP_NAME  // Windows推荐使用应用首字母大写
        )
    }

    private fun getMacConfigDir(): File {
        return File(
            System.getProperty("user.home"),
            "Library/Application Support/${BuildConfig.APP_NAME.lowercase()}"  // macOS标准应用支持目录
        )
    }

    private fun getLinuxConfigDir(): File {
        val baseDir = System.getenv("XDG_CONFIG_HOME")
            ?: File(System.getProperty("user.home"), ".config").path
        return File(baseDir, BuildConfig.APP_NAME.lowercase())
    }

    private fun getFallbackConfigDir(): File {
        return File(System.getProperty("user.home"), ".${BuildConfig.APP_NAME.lowercase()}")
    }
}