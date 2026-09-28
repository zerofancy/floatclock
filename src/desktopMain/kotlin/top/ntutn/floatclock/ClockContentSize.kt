package top.ntutn.floatclock

import androidx.compose.ui.unit.IntSize

/** Retain the largest natural size until the appearance or display density changes. */
internal class ClockContentSize {
    private var size = IntSize.Zero

    fun include(width: Int, height: Int): IntSize {
        size = IntSize(maxOf(size.width, width), maxOf(size.height, height))
        return size
    }
}
