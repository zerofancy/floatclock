package top.ntutn.floatclock.clock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.ntutn.floatclock.component.AppComponent
import java.awt.Color
import java.awt.Graphics
import javax.swing.JPanel

class ClockPanel(private val appComponent: AppComponent) : JPanel() {

    init {
        isOpaque = false
        background = Color(255, 255, 255, 0)

        appComponent.themeComponent.subscribe {
            val newDimension = it.measure()
            appComponent.floatWindowLayout(newDimension.width, newDimension.height)
            repaint()
            it.getTextColor().subscribe {
                repaint()
            }
        }

        GlobalScope.launch(Dispatchers.Main) {
            while (true) {
                delay(30 * 1000L)
                repaint()
            }
        }
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g?:return)
        g.clearRect(0, 0, width, height)
        appComponent.themeComponent.value.paint(g, width, height)
    }
}