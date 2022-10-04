package top.ntutn.floatclock

import androidx.compose.foundation.text.InternalFoundationTextApi
import androidx.compose.foundation.text.TextDelegate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontLoader
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import kotlin.math.roundToInt
import kotlin.random.Random

abstract class ClockComponent(val density: Density, val resourceLoader: androidx.compose.ui.text.font.Font.ResourceLoader): BaseClockComponent() {
    val textColor = mutableStateOf(randomColor())

    @OptIn(InternalFoundationTextApi::class)
    override fun measure(): Dimension {
        val textDelegate = TextDelegate(
            text = AnnotatedString("88:88"),
            style = TextStyle(fontFamily = getFontFamily(), fontSize = getFontSize()),
            density = density,
            resourceLoader = resourceLoader
        )
        val layoutResult = textDelegate.layout(Constraints(), LayoutDirection.Ltr)
        return Dimension((layoutResult.size.width).toInt(), (layoutResult.size.height).toInt())
    }

    open fun getFontFamily(): FontFamily? = null

    open fun getFontSize() = 4.em

    fun changeColor() {
        textColor.value = randomColor()
    }

    /**
     * 拍脑袋想的随机颜色算法
     */
    @OptIn(ExperimentalGraphicsApi::class)
    private fun randomColor(): Color {
        val h = (0..360).random().toFloat()
        val s = Random.nextFloat()
        var l = Random.nextFloat()
        while (l < 0.3f || l > 0.8) {
            l = Random.nextFloat()
        }
        return Color.hsl(h, s, l)
    }
}