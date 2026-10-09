package top.ntutn.floatclock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import top.ntutn.floatclock.generated.resources.Res
import top.ntutn.floatclock.generated.resources.clock

private data class Acknowledgement(
    val name: String,
    val url: String,
    val description: String,
)

// Keep these credits aligned with the acknowledgements in README.md.
private val acknowledgements = listOf(
    Acknowledgement(
        "Compose Multiplatform",
        "https://github.com/JetBrains/compose-multiplatform",
        "构建桌面界面、悬浮时钟窗口与交互。",
    ),
    Acknowledgement(
        "Jetpack DataStore (Okio)",
        "https://developer.android.com/topic/libraries/architecture/datastore",
        "持久化保存颜色、时钟样式等应用设置。",
    ),
    Acknowledgement(
        "Kotlinx Coroutines",
        "https://github.com/Kotlin/kotlinx.coroutines",
        "处理异步任务、状态流、定时刷新与 Swing 线程调度。",
    ),
    Acknowledgement(
        "Kotlinx Serialization",
        "https://github.com/Kotlin/kotlinx.serialization",
        "序列化应用设置，并解析中国色 JSON 数据。",
    ),
    Acknowledgement(
        "OSHI",
        "https://github.com/oshi/oshi",
        "读取网卡收发字节数，用于计算和显示实时网速。",
    ),
    Acknowledgement(
        "SLF4J",
        "https://www.slf4j.org/",
        "提供统一的应用日志 API。",
    ),
    Acknowledgement(
        "Logback",
        "https://logback.qos.ch/",
        "提供日志运行时实现，按配置输出应用日志。",
    ),
    Acknowledgement(
        "digital-7 字体",
        "https://www.dafont.com/digital-7.font",
        "提供数码管样式的时钟字体。",
    ),
    Acknowledgement(
        "中国色",
        "https://zhongguose.com/",
        "提供中国传统颜色数据，用于前景色菜单与随机换色。",
    ),
    Acknowledgement(
        "zClock Lite",
        "https://apps.apple.com/us/app/zclock-lite-topmost-clock/id1489475245?mt=12",
        "提供桌面置顶时钟的产品灵感。",
    ),
)

@Composable
fun AboutContent() {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        val centered = Modifier.align(Alignment.CenterHorizontally)
        val projectUrl = "https://github.com/zerofancy/floatclock"

        Image(
            painter = painterResource(Res.drawable.clock),
            contentDescription = null,
            modifier = centered.size(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text("${BuildConfig.APP_NAME} ${BuildConfig.APP_VERSION}", modifier = centered)
        Spacer(Modifier.height(8.dp))
        Text("作者：归零幻想 (zerofancy)", modifier = centered)
        Spacer(Modifier.height(8.dp))
        AboutLink(projectUrl, projectUrl, modifier = centered)

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
        Text("致谢", style = MaterialTheme.typography.h6)
        Spacer(Modifier.height(8.dp))
        Text("本项目参考或使用了以下项目与资源。")
        acknowledgements.forEach { credit ->
            Spacer(Modifier.height(12.dp))
            AboutLink(credit.name, credit.url)
            Spacer(Modifier.height(4.dp))
            Text(credit.description, style = MaterialTheme.typography.body2)
        }
    }
}

@Composable
private fun AboutLink(label: String, url: String, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withLink(
                LinkAnnotation.Url(
                    url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                    ),
                ),
            ) {
                append(label)
            }
        },
        modifier = modifier,
    )
}
