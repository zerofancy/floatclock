@file:JvmName("FloatClock")

package top.ntutn.floatclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.ntutn.floatclock.macos.MacOSWindowBridge
import top.ntutn.floatclock.storage.DataStoreFactory
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Toolkit
import java.awt.Window
import java.text.SimpleDateFormat
import javax.swing.JDialog
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.window.Window as ComposeWindow
import java.awt.Color as AwtColor

private const val OVERLAY_WINDOW_TITLE_PREFIX = "__floatclock_overlay__"
private const val CLOCK_WINDOW_WIDTH = 180
private const val CLOCK_WINDOW_HEIGHT = 80
private val DefaultClockColor = Color(0xFF1A3B32)
private val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

// 限定在清晰、相对美观的色值范围内随机（HSB 色彩空间）
private const val MIN_HUE_DISTANCE = 0.08f
private val HSB_SATURATION_RANGE = 0.55f..0.85f
private val HSB_BRIGHTNESS_RANGE = 0.45f..0.75f

private val PRESET_CLOCK_COLORS = mapOf(
    "高粱红" to "#c02c38",
    "淡橘橙" to "#fba414",
    "藤黄" to "#ffd111",
    "深海绿" to "#1a3b32",
    "钢蓝" to "#0f1423",
    "靛青" to "#1661ab",
    "檀紫" to "#381924",
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val graphicsConfigurations = remember { overlayGraphicsConfigurations() }
    var text by remember { mutableStateOf("00:00") }
    var aboutVisible by remember { mutableStateOf(false) }
    var clockTextColor by remember { mutableStateOf(DefaultClockColor) }
    val themeDataStore = remember { DataStoreFactory().createThemeDataStore() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("HH:mm")
        while (true) {
            text = dateFormat.format(System.currentTimeMillis())
            delay(500.milliseconds)
        }
    }

    LaunchedEffect(themeDataStore) {
        themeDataStore.themeData().collect { model ->
            clockTextColor = Color(AwtColor(model.colorR, model.colorG, model.colorB).rgb)
        }
    }

    val contextMenu = remember {
        JPopupMenu().apply {
            JMenu("选择颜色").apply {
                PRESET_CLOCK_COLORS.forEach { (name, hex) ->
                    add(name).addActionListener {
                        val awtColor = AwtColor.decode(hex)
                        clockTextColor = Color(awtColor.rgb)
                        scope.launch { themeDataStore.updateColor(awtColor) }
                    }
                }
                addSeparator()
                add("随机").addActionListener {
                    val awtColor = randomPleasingColor(clockTextColor)
                    clockTextColor = Color(awtColor.rgb)
                    scope.launch { themeDataStore.updateColor(awtColor) }
                }
            }.also { add(it) }
            add("关于").addActionListener { aboutVisible = true }
            add("退出").addActionListener { exitApplication() }
        }
    }

    if (aboutVisible) {
        ComposeWindow(
            onCloseRequest = { aboutVisible = false },
            title = "关于 ${BuildConfig.APP_NAME}",
            state = rememberWindowState(width = 400.dp, height = 300.dp),
            resizable = false,
        ) {
            AboutContent()
        }
    }

    graphicsConfigurations.forEachIndexed { index, graphicsConfiguration ->
        val windowTitle = "$OVERLAY_WINDOW_TITLE_PREFIX$index"
        DialogWindow(
            create = {
                ComposeDialog(graphicsConfiguration = graphicsConfiguration).apply {
                    // POPUP is created as a non-activating NSPanel by OpenJDK on macOS.
                    type = Window.Type.POPUP
                    title = windowTitle
                    isUndecorated = true
                    isTransparent = true
                    isResizable = false
                    focusableWindowState = false
                    isAutoRequestFocus = false
                    isAlwaysOnTop = true
                    defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE

                    // Ensure the native peer is created only after all immutable window
                    // properties (especially type) have been applied.
                    // HH:mm has a fixed shape. Keep a stable window size instead of feeding
                    // a constrained Text layout result back into the native window size.
                    preferredSize = Dimension(CLOCK_WINDOW_WIDTH, CLOCK_WINDOW_HEIGHT)
                    pack()
                    preferredSize = null
                    setSize(CLOCK_WINDOW_WIDTH, CLOCK_WINDOW_HEIGHT)
                    moveToScreenBottomEnd(this, graphicsConfiguration)
                }
            },
            dispose = ComposeDialog::dispose,
            update = { dialog ->
                dialog.isAlwaysOnTop = true
            },
        ) {
            FloatClockContent(
                windowTitle = windowTitle,
                text = text,
                textColor = clockTextColor,
                contextMenu = contextMenu,
            )
        }
    }
}

@Composable
private fun DialogWindowScope.FloatClockContent(
    windowTitle: String,
    text: String,
    textColor: Color,
    contextMenu: JPopupMenu,
) {
    val dialog = window

    Box(
        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        event.changes.forEach { it.consume() }
                        val pointerLocation = MouseInfo.getPointerInfo()?.location
                        if (pointerLocation != null) {
                            SwingUtilities.invokeLater {
                                contextMenu.location = pointerLocation
                                contextMenu.invoker = contextMenu
                                contextMenu.isVisible = true
                            }
                        }
                    }
                }
            }
        },
    ) {
        WindowDraggableArea {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text, fontSize = 48.sp, maxLines = 1, color = textColor)
            }
        }
    }

    if (isMacOS) {
        LaunchedEffect(dialog, windowTitle) {
            repeat(20) {
                val configured = withContext(Dispatchers.Default) {
                    MacOSWindowBridge.applyOverlayBehavior(windowTitle)
                }
                if (configured) {
                    return@LaunchedEffect
                }
                delay(50)
            }
            System.err.println("Unable to find the FloatClock NSWindow to configure: $windowTitle")
        }
    }
}

private fun overlayGraphicsConfigurations(): List<GraphicsConfiguration> {
    val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
    if (isMacOS) {
        // A window has one physical screen position. Create one NSPanel per display so that
        // every display's independent full-screen Space has its own overlay instance.
        return graphicsEnvironment.screenDevices.map { it.defaultConfiguration }
    }

    val currentDevice = runCatching { MouseInfo.getPointerInfo()?.device }.getOrNull()
    return listOf((currentDevice ?: graphicsEnvironment.defaultScreenDevice).defaultConfiguration)
}

private fun moveToScreenBottomEnd(window: Window, graphicsConfiguration: GraphicsConfiguration) {
    val screenBounds = graphicsConfiguration.bounds
    val screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration)
    val x = screenBounds.x + screenBounds.width - screenInsets.right - window.width
    val y = screenBounds.y + screenBounds.height - screenInsets.bottom - window.height
    window.setLocation(x, y)
}

private fun randomPleasingColor(currentColor: Color): AwtColor {
    val rgb = (currentColor.value and 0xFFFFFFu).toInt()
    val currentHue = AwtColor.RGBtoHSB(
        (rgb shr 16) and 0xFF,
        (rgb shr 8) and 0xFF,
        rgb and 0xFF,
        null,
    )[0]
    var hsb: FloatArray
    do {
        hsb = floatArrayOf(
            Random.nextFloat(),
            HSB_SATURATION_RANGE.start + Random.nextFloat() * (HSB_SATURATION_RANGE.endInclusive - HSB_SATURATION_RANGE.start),
            HSB_BRIGHTNESS_RANGE.start + Random.nextFloat() * (HSB_BRIGHTNESS_RANGE.endInclusive - HSB_BRIGHTNESS_RANGE.start),
        )
    } while (hueDistance(hsb[0], currentHue) < MIN_HUE_DISTANCE)
    return AwtColor.getHSBColor(hsb[0], hsb[1], hsb[2])
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b)
    return minOf(d, 1f - d)
}
