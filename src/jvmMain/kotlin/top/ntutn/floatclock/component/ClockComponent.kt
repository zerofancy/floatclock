package top.ntutn.floatclock.component

import top.ntutn.floatclock.coloredit.ColorEditPanel
import top.ntutn.floatclock.coloredit.showEditPanel
import top.ntutn.floatclock.decompose.MutableValue
import top.ntutn.floatclock.decompose.Value
import top.ntutn.floatclock.storage.intPropertyConfig
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random

abstract class ClockComponent : IClockComponent {
    companion object {
        val sdf = SimpleDateFormat("hh:mm")
    }
    private var lastUsedColorR by intPropertyConfig("key_last_color_red")
    private var lastUsedColorG by intPropertyConfig("key_last_color_green")
    private var lastUsedColorB by intPropertyConfig("key_last_color_blue")

    val textColor = MutableValue(getLastUsedColor() ?: saveColor(randomColor()))
    private var mFont: Font? = null

    private fun getLastUsedColor(): Color? {
        val r = lastUsedColorR ?: return null
        val g = lastUsedColorG ?: return null
        val b = lastUsedColorB ?: return null
        return Color(r, g, b)
    }

    override fun changeColor(colorString: String?) {
        val color = colorString?.let {
            Color.decode(it)
        } ?: textColor.value
        textColor.value = saveColor(color)
    }

    override fun showEditColorPanel() {
        val color = textColor.value
        val r = color.red
        val g = color.green
        val b = color.blue
        ColorEditPanel.showEditPanel("#${Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b)}", ::changeColor)
    }

    private fun saveColor(color: Color): Color {
        lastUsedColorR = color.red
        lastUsedColorG = color.green
        lastUsedColorB = color.blue
        return color
    }

    override fun getTextColor(): Value<Color> = textColor

    override fun measure(): Dimension {
        // https://stackoverflow.com/a/8267630
        val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
        val font = getFontNotNull(g)
        g.font = font
        val bounds = font.getStringBounds(sdf.format(Date(1970, 1, 1, 8, 8, 8)), g.fontMetrics.fontRenderContext)
        return Dimension(bounds.width.roundToInt(), bounds.height.roundToInt())
    }

    protected open fun getFont(): Font? = null

    private fun getFontNotNull(g: Graphics? = null): Font {
        val g = g ?: BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
        return (getFont() ?: g.font).deriveFont(48f)
    }

    override fun paint(g: Graphics, width: Int, height: Int) {
        if (mFont == null) {
            mFont = getFont()?.deriveFont(48f)
        }
        g.font = mFont ?: getFontNotNull(g)
        g.color = textColor.value
        g.drawString(sdf.format(System.currentTimeMillis()), 0, g.fontMetrics.ascent)
    }

    /**
     * 随机颜色
     */
    private fun randomColor(): Color {
        val h = Random.nextFloat()
        val s = Random.nextFloat()
        val l = Random.nextFloat()
        return Color.getHSBColor(h, s, l).brighter()
    }
}