package top.ntutn.floatclock

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat

val sdf = SimpleDateFormat("hh:mm")

@Composable
@Preview
fun Clock(color: Color = Color.Red, fontFamily: FontFamily? = null, fontSize: TextUnit) {
    var text by remember { mutableStateOf("88:88") }
    Text(text = text, fontSize = fontSize, overflow = TextOverflow.Visible, maxLines = 1, color = color, fontFamily = fontFamily)
    LaunchedEffect(null) {
        while (true) {
            text = sdf.format(System.currentTimeMillis())
            delay(1000L)
        }
    }
}

@Composable
fun NormalClock(clockComponent: NormalClockComponent) {
    Clock(clockComponent.textColor.value, clockComponent.getFontFamily(), clockComponent.getFontSize())
}

@Composable
fun DigitalClock(clockComponent: DigitalClockComponent) {
    Clock(clockComponent.textColor.value, clockComponent.getFontFamily(), clockComponent.getFontSize())
}
