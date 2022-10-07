package top.ntutn.floatclock.component

import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

class DigitalClockComponent : ClockComponent() {
    override fun getFont(): Font {
        return Font.createFont(Font.TRUETYPE_FONT, javaClass.classLoader.getResourceAsStream("digital-7.ttf"))
    }

    override fun measure(): Dimension {
        val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
        val font = getFont().deriveFont(48f)
        g.font = font
        return Dimension(
            font.getStringBounds(sdf.format(Date(1970, 1, 1, 8, 8, 8)), g.fontMetrics.fontRenderContext).width.roundToInt(),
            abs(g.fontMetrics.ascent) + abs(g.fontMetrics.descent) + abs(g.fontMetrics.leading)
        )
    }
}