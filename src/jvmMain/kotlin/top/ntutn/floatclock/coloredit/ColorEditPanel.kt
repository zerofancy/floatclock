package top.ntutn.floatclock.coloredit

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import java.awt.Dimension
import javax.swing.JFrame

object ColorEditPanel

fun ColorEditPanel.showEditPanel(lastColor: java.awt.Color, editColorCallback: (java.awt.Color) -> Unit) {
    ComposeWindow().also {
        it.setContent {
            ColorEditContent(lastColor, editColorCallback)
        }
        it.size = Dimension(400, 300)
        it.setLocationRelativeTo(null)

        it.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        it.isVisible = true
    }
}

@Composable
fun ColorEditContent(lastColor: java.awt.Color, editColorCallback: (java.awt.Color) -> Unit) {
    Column {
        var red by remember { mutableStateOf(0) }
        var green by remember { mutableStateOf(0) }
        var blue by remember { mutableStateOf(0) }

        LaunchedEffect(lastColor) {
            red = lastColor.red
            green = lastColor.green
            blue = lastColor.blue
        }

        Text(text = "#${Integer.toHexString(red) + Integer.toHexString(green) + Integer.toHexString(blue)}", color = Color(red, green, blue), fontSize = 20.sp)

        val onSelected = {
            editColorCallback(java.awt.Color(red, green, blue))
        }

        Slider(colors = SliderDefaults.colors(thumbColor = Color(red, 0, 0)), value = red.toFloat(), onValueChange =  {
            red = it.toInt()
        }, onValueChangeFinished = onSelected, valueRange = 0f..255f, steps = 256)
        Slider(colors = SliderDefaults.colors(thumbColor = Color(0, green, 0)), value = green.toFloat(), onValueChange =  {
            green = it.toInt()
        }, onValueChangeFinished = onSelected, valueRange = 0f..255f, steps = 256)
        Slider(colors = SliderDefaults.colors(thumbColor = Color(0, 0, blue)), value = blue.toFloat(), onValueChange =  {
            blue = it.toInt()
        }, onValueChangeFinished = onSelected, valueRange = 0f..255f, steps = 256)
    }
}
