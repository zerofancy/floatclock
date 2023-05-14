package top.ntutn.floatclock.clock

import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu

class ContextMenu(
    private val themeAction: () -> Unit,
    private val colorAction: (String?) -> Unit,
    private val colorEditAction: () -> Unit,
    private val aboutAction: () -> Unit,
    private val exitAction: () -> Unit
) : JPopupMenu() {
    init {
        add("主题").addActionListener { themeAction() }
        JMenu("颜色").apply {
            mapOf(
                "高粱红" to "#c02c38",
                "淡橘橙" to "#fba414",
                "藤黄" to "#ffd111",
                "深海绿" to "#1a3b32",
                "钢蓝" to "#0f1423",
                "靛青" to "#1661ab",
                "檀紫" to "#381924",
            ).forEach { (colorName, colorString) ->
                add(colorName).addActionListener {
                    colorAction(colorString)
                }
            }
            add(JMenuItem().also {
                it.isEnabled = false
            })
            add("随机").addActionListener { colorAction(null) }
            add("编辑").addActionListener { colorEditAction() }
        }.also {
            add(it)
        }
        add("关于").addActionListener { aboutAction() }
        add("退出").addActionListener { exitAction() }
    }
}