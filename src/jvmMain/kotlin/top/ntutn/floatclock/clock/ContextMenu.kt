package top.ntutn.floatclock.clock

import javax.swing.JPopupMenu

class ContextMenu(
    private val themeAction: () -> Unit,
    private val colorAction: () -> Unit,
    private val aboutAction: () -> Unit,
    private val exitAction: () -> Unit
) : JPopupMenu() {
    init {
        add("切换主题").addActionListener { themeAction() }
        add("更换颜色").addActionListener { colorAction() }
        add("关于").addActionListener { aboutAction() }
        add("退出").addActionListener { exitAction() }
    }
}