package top.ntutn.floatclock.storage
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

@Serializable
data class ThemeModel(val theme: String, val colorR: Int, val colorG: Int, val colorB: Int) {
    object Serializer: OkioSerializer<ThemeModel> {
        private val jsonClient = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true  // 自动转换无效值为默认值
            explicitNulls = false     // 不序列化null值字段
        }

        override val defaultValue: ThemeModel = ThemeModel("", 0, 0, 0)

        override suspend fun readFrom(source: BufferedSource): ThemeModel {
            return jsonClient.decodeFromString(source.readUtf8())
        }

        override suspend fun writeTo(
            t: ThemeModel,
            sink: BufferedSink
        ) {
            sink.use {
                it.writeUtf8(jsonClient.encodeToString(t))
            }
        }
    }
}