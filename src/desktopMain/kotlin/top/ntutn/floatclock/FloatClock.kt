@file:JvmName("FloatClock")
package top.ntutn.floatclock

import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = BuildConfig.APP_NAME,
    ) {
        var text by remember { mutableStateOf("00:00") }

        Text(text)

        val scope = rememberCoroutineScope()
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
