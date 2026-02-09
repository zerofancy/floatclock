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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun main() = application {
    val state = rememberWindowState()
    Window(
        onCloseRequest = ::exitApplication,
        title = BuildConfig.APP_NAME,
        state = state,
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false
    ) {
        WindowDraggableArea {
            var text by remember { mutableStateOf("00:00") }

            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            
            Text(
                text = text, 
                fontSize = 48.sp,
                onTextLayout = { result ->
                    scope.launch {
                        with(density) {
                            state.size = state.size.copy(
                                width = result.size.width.toDp(),
                                height = result.size.height.toDp()
                            )
                        }
                    }
                }
            )
            
            LaunchedEffect(Unit) {
                scope.launch(Dispatchers.Default) {
                    val formatter = DateTimeFormatter.ofPattern("HH:mm")
                    try {
                        while (true) {
                            text = LocalTime.now().format(formatter)
                            delay(500)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}