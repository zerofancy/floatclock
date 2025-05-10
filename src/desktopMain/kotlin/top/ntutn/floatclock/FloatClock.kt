@file:JvmName("FloatClock")

package top.ntutn.floatclock

import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

fun main() = application {
    val state = rememberWindowState()
    Window(
        onCloseRequest = ::exitApplication,
        title = BuildConfig.APP_NAME,
        state = state,
        undecorated = true,
    ) {
        WindowDraggableArea() {
            var text by remember { mutableStateOf("00:00") }

            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            Text(text, fontSize = 48.sp, onTextLayout = { result ->
                scope.launch {
                    delay(8)
                    with(density) {
                        state.size = state.size.copy(
                            width = result.size.width.toDp(),
                            height = result.size.height.toDp()
                        )
                    }
                }
            })
            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.Default) {
                    val sdf = SimpleDateFormat("HH:mm")
                    while (true) {
                        text = sdf.format(System.currentTimeMillis())
                        delay(500)
                    }
                }
            }
        }
    }
}