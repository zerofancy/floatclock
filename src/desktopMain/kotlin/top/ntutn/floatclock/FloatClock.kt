@file:JvmName("FloatClock")

package top.ntutn.floatclock

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.DialogWindowScope
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.ntutn.floatclock.macos.MacOSWindowBridge
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Toolkit
import java.awt.Window
import java.text.SimpleDateFormat
import javax.swing.JDialog

private const val OVERLAY_WINDOW_TITLE_PREFIX = "__floatclock_overlay__"
private const val CLOCK_WINDOW_WIDTH = 180
private const val CLOCK_WINDOW_HEIGHT = 80
private val isMacOS = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val graphicsConfigurations = remember { overlayGraphicsConfigurations() }
    var text by remember { mutableStateOf("00:00") }

    LaunchedEffect(Unit) {
        val dateFormat = SimpleDateFormat("HH:mm")
        while (true) {
            text = dateFormat.format(System.currentTimeMillis())
            delay(500)
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
            )
        }
    }
}

@Composable
private fun DialogWindowScope.FloatClockContent(
    windowTitle: String,
    text: String,
) {
    val dialog = window

    WindowDraggableArea {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, fontSize = 48.sp, maxLines = 1)
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
