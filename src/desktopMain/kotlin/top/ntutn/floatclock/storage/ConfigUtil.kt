package top.ntutn.floatclock.storage

import org.apache.commons.lang3.SystemUtils
import org.mapdb.DB
import org.mapdb.DBException
import org.mapdb.DBMaker
import org.mapdb.Serializer
import top.ntutn.floatclock.util.MsgBox
import java.io.File
import kotlin.system.exitProcess

object ConfigUtil {
    private lateinit var configDb: DB
    lateinit var stringConfigMap: MutableMap<String, String?>
    lateinit var intConfigMap: MutableMap<String, Int?>

    fun init() {
        val userHome = System.getProperty("user.home")
        val dbPath = if (SystemUtils.IS_OS_WINDOWS) {
            File(System.getenv("LOCALAPPDATA"), "floatclock")
        } else {
            File(userHome, ".config/floatclock")
        }
        dbPath.mkdirs()
        val dbFile = File(dbPath, "config.mpdb")
        try {
            configDb = DBMaker.fileDB(dbFile)
                .fileMmapEnable()
                .checksumHeaderBypass()
                .closeOnJvmShutdown()
                .make()
        } catch (e: DBException.FileLocked) {
            MsgBox.Builder()
                .title("DBException\$FileLocked")
                .message("配置数据库锁定失败，请检查是否已经运行了一个实例。")
                .type(MsgBox.MessageType.ERROR)
                .model()
                .build()
                .show()
            exitProcess(1)
        }

        stringConfigMap = configDb.hashMap("string_config_map", Serializer.STRING, Serializer.STRING).createOrOpen()
        intConfigMap = configDb.hashMap("int_config_map", Serializer.STRING, Serializer.INTEGER).createOrOpen()
    }
}