package top.ntutn.floatclock.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import top.ntutn.floatclock.coloredit.ColorEditPanel
import top.ntutn.floatclock.coloredit.showEditPanel
import top.ntutn.floatclock.storage.ThemeDataStore
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import kotlin.random.Random

abstract class ClockComponent(open val themeDataStore: ThemeDataStore) : IClockComponent {
    companion object {
        val sdf = SimpleDateFormat("hh:mm")
    }

    private var mFont: Font? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val textColorFlow: MutableStateFlow<Color> = MutableStateFlow(Color.red)

    init {
        themeDataStore.themeData()
            .map { Color(it.colorR, it.colorG, it.colorB) }
            .onEach {
                textColorFlow.value = it
            }
            .launchIn(coroutineScope)
    }

    private fun updateColor(color: Color) {
        coroutineScope.launch {
            themeDataStore.updateColor(color)
        }
    }

    override fun changeColor(colorString: String?) {
        val color = colorString?.let {
            Color.decode(it)
        } ?: randomColor()
        updateColor(color)
    }

    override fun showEditColorPanel() {
        val color = textColorFlow.value
        ColorEditPanel.showEditPanel(color) {
            updateColor(it)
        }
    }

    override fun getTextColorFlow(): Flow<Color> {
        return textColorFlow
    }

    override fun measure(): Dimension {
        // https://stackoverflow.com/a/8267630
        val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
        val font = getFontNotNull(g)
        g.font = font
        val bounds = font.getStringBounds(
            sdf.format(44_520_000), // placeholder
            g.fontMetrics.fontRenderContext
        )
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
        g.color = textColorFlow.value
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

    override fun destroy() {
        coroutineScope.cancel()
    }
}