package top.ntutn.floatclock

import top.ntutn.floatclock.storage.ThemeModel
import javax.swing.ButtonGroup
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JRadioButtonMenuItem

/** Owned by the UI/EDT thread, just like ClockSettings. All windows share this menu. */
internal class ForegroundColorMenu(
    private val palette: ForegroundPalette,
    private val settings: ClockSettings,
) {
    val menu = JMenu("前景色").apply { isEnabled = false }
    private val randomItem = JMenuItem("随机").apply { addActionListener { applyRandomColor() } }
    private val items = mutableListOf<JRadioButtonMenuItem>()
    private var selection: ForegroundSelection? = null

    fun refresh() {
        menu.isEnabled = settings.ready
        if (!settings.ready) return
        val rgb = settings.model.foregroundRgb()
        val current = selection
        selection = if (current == null) palette.initialSelection(rgb)
        else current.select(palette.resolve(rgb))
        if (current == null) {
            val group = ButtonGroup()
            selection!!.colors.indices.forEach { index ->
                val item = JRadioButtonMenuItem().apply {
                    addActionListener { applyColor(selection!!.colors[index].rgb) }
                }
                items.add(item)
                group.add(item)
                menu.add(item)
            }
            menu.addSeparator()
            menu.add(randomItem)
        }
        val updated = selection!!
        items.forEachIndexed { index, item ->
            item.text = updated.colors[index].name
            item.toolTipText = updated.colors[index].hex
            item.isSelected = index == updated.selectedIndex
        }
        randomItem.isEnabled = palette.colors.any { it.rgb != rgb }
    }

    fun applyRandomColor() {
        if (!settings.ready) return
        refresh()
        val color = palette.randomColor(settings.model.foregroundRgb()) ?: return
        applyColor(color.rgb)
    }

    private fun applyColor(rgb: Int) {
        if (!settings.ready) return
        if (settings.model.foregroundRgb() != rgb) {
            // Capture a fixed RGB: ClockSettings can replay this transform after a failed save.
            settings.update { it.copy(colorR = rgb shr 16, colorG = (rgb shr 8) and 255, colorB = rgb and 255) }
        }
        refresh()
    }
}

internal fun ThemeModel.foregroundRgb(): Int = (colorR shl 16) or (colorG shl 8) or colorB
