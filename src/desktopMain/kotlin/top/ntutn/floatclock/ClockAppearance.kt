package top.ntutn.floatclock

import androidx.compose.ui.graphics.Color
import top.ntutn.floatclock.storage.ThemeModel

internal val OutlineColors = linkedMapOf("black" to Color.Black, "white" to Color.White)

internal data class ClockAppearance(
    val foreground: Color,
    val background: Color,
    val style: String,
    val showNetSpeed: Boolean,
    val showOutline: Boolean,
    val outlineName: String,
) {
    val outlineColor: Color get() = OutlineColors.getValue(outlineName)
}

internal fun ThemeModel.appearance() = ClockAppearance(
    foreground = Color(colorR, colorG, colorB),
    background = if (showOutline) Color.Transparent else when (backgroundColor) {
        "black" -> Color.Black
        "white" -> Color.White
        else -> Color.Transparent
    },
    style = if (theme == "normal") "normal" else "digital",
    showNetSpeed = showNetSpeed,
    showOutline = showOutline,
    outlineName = outlineColor.takeIf { it in OutlineColors } ?: "white",
)
