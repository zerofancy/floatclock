package top.ntutn.floatclock.component

import kotlinx.coroutines.flow.Flow
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics

interface IClockComponent {
    fun measure(): Dimension

    fun paint(g: Graphics, width: Int, height: Int)

    fun getTextColorFlow(): Flow<Color>

    fun changeColor(colorString: String?)

    fun showEditColorPanel()

    fun destroy()
}