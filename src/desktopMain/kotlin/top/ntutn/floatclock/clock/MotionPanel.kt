package top.ntutn.floatclock.clock

import java.awt.Point
import java.awt.event.*
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * 无标题拖动
 * https://stackoverflow.com/a/13171534
 */
class MotionPanel(private val parent: JDialog, private val rightClickCallback: (MouseEvent) -> Unit) : JPanel() {
    private var initialClick: Point? = null

    init {
        setBounds(0, 0, parent.width, parent.height)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    rightClickCallback(e)
                    return
                }
                initialClick = e.point
                getComponentAt(initialClick)
            }
        })
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    return
                }
                val initialClick = initialClick ?: return

                // get location of Window
                val thisX = parent.location.x
                val thisY = parent.location.y

                // Determine how much the mouse moved since the initial click
                val xMoved: Int = e.x - initialClick.x
                val yMoved: Int = e.y - initialClick.y

                // Move window to this position
                val x = thisX + xMoved
                val y = thisY + yMoved
                parent.setLocation(x, y)
            }
        })
        isOpaque = false
    }
}