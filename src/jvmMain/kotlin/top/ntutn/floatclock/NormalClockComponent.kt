package top.ntutn.floatclock

import androidx.compose.ui.unit.Density
import java.awt.Dimension

class NormalClockComponent(density: Density,
                           resourceLoader: androidx.compose.ui.text.font.Font.ResourceLoader
) : ClockComponent(density, resourceLoader) {
    override fun measure(): Dimension {
        return super.measure()
    } }