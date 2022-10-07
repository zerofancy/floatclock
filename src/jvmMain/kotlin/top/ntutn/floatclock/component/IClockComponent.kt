package top.ntutn.floatclock.component

import top.ntutn.floatclock.decompose.Value
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics

interface IClockComponent {
    fun measure(): Dimension

    fun paint(g: Graphics, width: Int, height: Int)

    fun getTextColor(): Value<Color>

    fun changeColor()
}