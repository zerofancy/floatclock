package top.ntutn.floatclock.storage

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ConfigDelegate<T>(private val readDelegate: () -> T?, private val writeDelegate: (T?) -> Unit) :
    ReadWriteProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return readDelegate()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        writeDelegate(value)
    }
}

/**
 * 存储一些简单string类型应用配置
 */
fun stringPropertyConfig(key: String) =
    ConfigDelegate({ ConfigUtil.stringConfigMap[key] }, { ConfigUtil.stringConfigMap[key] = it })

/**
 * 存储一些简单int类型应用配置
 */
fun intPropertyConfig(key: String) =
    ConfigDelegate({
        ConfigUtil.intConfigMap[key]
    }, {
        ConfigUtil.intConfigMap[key] = it
    })

