package top.ntutn.floatclock.coloredit

import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Dimension
import javax.swing.JFrame

object ColorEditPanel

fun ColorEditPanel.showEditPanel(lastColor: String, editColorCallback: (String) -> Unit) {
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
fun ColorEditContent(lastColor: String, editColorCallback: (String) -> Unit) {
    var colorString by remember { mutableStateOf(lastColor) }
    TextField(colorString, onValueChange = {
        colorString = it
        editColorCallback(it)
    })
}