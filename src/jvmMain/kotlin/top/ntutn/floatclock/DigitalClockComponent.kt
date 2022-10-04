package top.ntutn.floatclock

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density

class DigitalClockComponent(density: Density,
                            resourceLoader: androidx.compose.ui.text.font.Font.ResourceLoader
) : ClockComponent(density, resourceLoader) {
    override fun getFontFamily(): FontFamily? {
        return FontFamily(Font("digital-7.ttf"))
    }
}