@file:JvmName("FloatClock")

package top.ntutn.floatclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import top.ntutn.floatclock.macos.MacOSWindowBridge
import top.ntutn.floatclock.net.NetSpeedMonitor
import top.ntutn.floatclock.net.humanBps
import top.ntutn.floatclock.storage.DataStoreFactory
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Toolkit
import java.awt.Window
import java.text.SimpleDateFormat
import javax.swing.JCheckBoxMenuItem
import javax.swing.JDialog
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.text.platform.Font as PlatformFont
import androidx.compose.ui.window.Window as ComposeWindow
import java.awt.Color as AwtColor

private const val OVERLAY_WINDOW_TITLE_PREFIX = "__floatclock_overlay__"
private const val MENU_DISMISS_TIMEOUT_MS = 1200L
private const val MENU_DISMISS_POLL_MS = 100L
private val DefaultClockColor = Color(0xFF1A3B32)
private const val DEFAULT_STYLE = "digital"
private val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
private val isAutoStartSupported: Boolean get() = AutoStart.isSupported()

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

private fun loadDigitalFontFamily(): FontFamily? {
    return runCatching {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("digital-7.ttf")
            ?: object {}::class.java.classLoader.getResourceAsStream("digital-7.ttf")
            ?: return@runCatching null
        val bytes = stream.use { it.readBytes() }
        val font: Font = PlatformFont(
            identity = "digital-7",
            data = bytes,
            weight = FontWeight.Normal,
            style = FontStyle.Normal,
        )
        FontFamily(font)
    }.getOrNull()
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Hide the Dock icon on macOS. Must be set before AWT/Toolkit initializes so no icon flashes.
    // Complements the same flag in build.gradle.kts jvmArgs for launches that bypass Gradle
    // (e.g. running main() directly from an IDE). No-op on other platforms.
    if (isMacOS) {
        System.setProperty("apple.awt.UIElement", "true")
    }
    application {
        val graphicsConfigurations = remember { overlayGraphicsConfigurations() }
        var text by remember { mutableStateOf("00:00") }
        var aboutVisible by remember { mutableStateOf(false) }
        var clockTextColor by remember { mutableStateOf(DefaultClockColor) }
        var clockStyle by remember { mutableStateOf(DEFAULT_STYLE) }
        var showNetSpeed by remember { mutableStateOf(false) }
        val themeDataStore = remember { DataStoreFactory().createThemeDataStore() }
        val scope = rememberCoroutineScope()
        val digitalFontFamily = remember { loadDigitalFontFamily() }
        val styleMenuDigitalItem = remember { JCheckBoxMenuItem("数码管样式") }
        val styleMenuNormalItem = remember { JCheckBoxMenuItem("普通样式") }
        val showNetSpeedMenuItem = remember { JCheckBoxMenuItem("显示网速") }
        val loginItemMenuItem = remember { JCheckBoxMenuItem("开机启动") }

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
                val resolvedStyle =
                    if (model.theme == "digital" || model.theme == "normal") model.theme else DEFAULT_STYLE
                if (clockStyle != resolvedStyle) {
                    clockStyle = resolvedStyle
                }
                if (showNetSpeed != model.showNetSpeed) {
                    showNetSpeed = model.showNetSpeed
                }
            }
        }

        // 同步样式菜单项的勾选状态
        LaunchedEffect(clockStyle) {
            styleMenuDigitalItem.isSelected = clockStyle == "digital"
            styleMenuNormalItem.isSelected = clockStyle == "normal"
        }

        LaunchedEffect(showNetSpeed) {
            showNetSpeedMenuItem.isSelected = showNetSpeed
        }

        // 检测当前是否已设置开机启动，同步复选项状态
        LaunchedEffect(Unit) {
            if (isAutoStartSupported) {
                val enabled = withContext(Dispatchers.Default) {
                    AutoStart.isEnabled()
                }
                System.err.println("[FloatClock] Initial autostart state: enabled=$enabled")
                loginItemMenuItem.isSelected = enabled
            }
        }

        val contextMenu = remember {
            JPopupMenu().apply {
                JMenu("时钟样式").apply {
                    styleMenuDigitalItem.addActionListener {
                        clockStyle = "digital"
                        scope.launch { themeDataStore.updateTheme("digital") }
                    }
                    styleMenuNormalItem.addActionListener {
                        clockStyle = "normal"
                        scope.launch { themeDataStore.updateTheme("normal") }
                    }
                    add(styleMenuDigitalItem)
                    add(styleMenuNormalItem)
                }.also { add(it) }
                addSeparator()
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
                addSeparator()
                showNetSpeedMenuItem.addActionListener {
                    scope.launch { themeDataStore.toggleShowNetSpeed() }
                }
                add(showNetSpeedMenuItem)
                if (isAutoStartSupported) {
                    addSeparator()
                    loginItemMenuItem.addActionListener {
                        // JCheckBoxMenuItem auto-toggles on click; isSelected is now the NEW desired state.
                        val target = loginItemMenuItem.isSelected
                        scope.launch {
                            val success = withContext(Dispatchers.Default) {
                                AutoStart.setEnabled(target)
                            }
                            if (!success) {
                                // Revert the auto-toggle if the operation failed.
                                loginItemMenuItem.isSelected = !target
                            }
                        }
                    }
                    add(loginItemMenuItem)
                }
                addSeparator()
                add("关于").addActionListener { aboutVisible = true }
                add("退出").addActionListener { exitApplication() }

                // 鼠标移出悬浮窗与菜单区域超过 1.2s 后自动关闭上下文菜单。
                // 应用为 LSUIElement 且悬浮窗 focusableWindowState=false，
                // 无法通过焦点丢失事件检测外部点击，故采用位置超时方案。
                val menu = this
                var dismissJob: Job? = null

                addPopupMenuListener(object : PopupMenuListener {
                    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                        dismissJob = scope.launch {
                            var outsideSince: Long? = null
                            while (menu.isVisible) {
                                delay(MENU_DISMISS_POLL_MS)
                                val inSafeZone = withContext(Dispatchers.Swing) {
                                    isPointerInAnyAppWindow()
                                }
                                if (inSafeZone) {
                                    outsideSince = null
                                } else {
                                    val now = System.currentTimeMillis()
                                    if (outsideSince == null) {
                                        outsideSince = now
                                    } else if (now - outsideSince >= MENU_DISMISS_TIMEOUT_MS) {
                                        withContext(Dispatchers.Swing) {
                                            if (menu.isVisible) menu.isVisible = false
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    }

                    override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                        dismissJob?.cancel()
                        dismissJob = null
                    }

                    override fun popupMenuCanceled(e: PopupMenuEvent?) {}
                })
            }
        }

        if (aboutVisible) {
            ComposeWindow(
                onCloseRequest = { aboutVisible = false },
                title = "关于 ${BuildConfig.APP_NAME}",
                state = rememberWindowState(width = 400.dp, height = 300.dp),
                resizable = false,
                alwaysOnTop = true,
            ) {
                AboutContent()
            }
        }

        graphicsConfigurations.forEachIndexed { index, graphicsConfiguration ->
            val windowTitle = "$OVERLAY_WINDOW_TITLE_PREFIX$index"

            // Both width and height: settle once per configuration change (clockStyle / showNetSpeed).
            // Use an initial large-enough size so the window is correct on the first frame.
            var desiredWindowHeight by remember { mutableStateOf(180) }
            var desiredWindowWidth by remember { mutableStateOf(260) }
            var sizeSettled by remember { mutableStateOf(false) }
            LaunchedEffect(showNetSpeed, clockStyle) { sizeSettled = false }

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
                        preferredSize = Dimension(desiredWindowWidth, desiredWindowHeight)
                        pack()
                        preferredSize = null
                        setSize(desiredWindowWidth, desiredWindowHeight)
                        moveToScreenBottomEnd(this, graphicsConfiguration)
                    }
                },
                dispose = ComposeDialog::dispose,
                update = { dialog ->
                    dialog.isAlwaysOnTop = true
                    if (dialog.height != desiredWindowHeight || dialog.width != desiredWindowWidth) {
                        println("set $desiredWindowWidth, $desiredWindowHeight")
                        dialog.setSize(desiredWindowWidth, desiredWindowHeight)
                        moveToScreenBottomEnd(dialog, graphicsConfiguration)
                    }
                },
            ) {
                FloatClockContent(
                    windowTitle = windowTitle,
                    text = text,
                    textColor = clockTextColor,
                    digitalFontFamily = digitalFontFamily,
                    clockStyle = clockStyle,
                    contextMenu = contextMenu,
                    showNetSpeed = showNetSpeed,
                    onContentSizeChanged = { w, h ->
                        if (!sizeSettled && w > 0 && h > 0) {
                            sizeSettled = true
                            desiredWindowWidth = w
                            desiredWindowHeight = h
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogWindowScope.FloatClockContent(
    windowTitle: String,
    text: String,
    textColor: Color,
    digitalFontFamily: FontFamily?,
    clockStyle: String,
    contextMenu: JPopupMenu,
    showNetSpeed: Boolean,
    onContentSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val dialog = window
    val fontFamily = if (clockStyle == "digital" && digitalFontFamily != null) digitalFontFamily else null

    val netSpeedMonitor = remember { NetSpeedMonitor(1000) }
    val cs = rememberCoroutineScope()
    DisposableEffect(Unit) {
        netSpeedMonitor.start(cs)
        onDispose { netSpeedMonitor.stop() }
    }
    val netSpeed by netSpeedMonitor.speed.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
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
        contentAlignment = Alignment.Center
    ) {
        WindowDraggableArea {
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    //.background(Color(0x55000000))
                    .onSizeChanged { size ->
                        if (size.height > 0) onContentSizeChanged(size.width, size.height)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text,
                        fontSize = 48.sp,
                        maxLines = 1,
                        color = textColor,
                        fontFamily = fontFamily,
                    )
                    if (showNetSpeed) {
                        Spacer(Modifier.height(if (clockStyle == "digital") 8.dp else 2.dp))
                        Text(
                            text = "↓ ${netSpeed.downBytesPerSec.humanBps()}   ↑ ${netSpeed.upBytesPerSec.humanBps()}",
                            fontSize = 14.sp,
                            maxLines = 1,
                            color = textColor,
                            fontFamily = fontFamily,
                        )
                    }
                }
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

/**
 * 鼠标指针是否落在本应用任一可见窗口内（悬浮窗、上下文菜单及其子菜单等）。
 * 用于判断鼠标是否离开了菜单交互区域。必须在 EDT 上调用。
 */
private fun isPointerInAnyAppWindow(): Boolean {
    val pointerLoc = MouseInfo.getPointerInfo()?.location ?: return false
    return Window.getWindows().any { it.isShowing && it.bounds.contains(pointerLoc) }
}
