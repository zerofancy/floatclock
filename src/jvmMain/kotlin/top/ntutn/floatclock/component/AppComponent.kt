package top.ntutn.floatclock.component

import top.ntutn.floatclock.decompose.MutableValue
import top.ntutn.floatclock.decompose.Value
import top.ntutn.floatclock.storage.stringPropertyConfig
import java.awt.Dimension

class AppComponent {
    private var lastUsedTheme by stringPropertyConfig("key_last_theme")

    private val _floatWindowSize = MutableValue(Dimension(200, 100))
    val floatWindowSize: Value<Dimension> get() = _floatWindowSize
    private val _themeComponent = MutableValue<IClockComponent>(if (lastUsedTheme == "digital") {
        DigitalClockComponent()
    } else {
        NormalClockComponent()
    })
    val themeComponent: Value<IClockComponent> get() = _themeComponent

    fun floatWindowLayout(width: Int, height: Int) {
        if (floatWindowSize.value.width != width || floatWindowSize.value.height != height) {
            _floatWindowSize.value = (Dimension(width, height))
        }
    }

    fun changeTheme() {
        if (_themeComponent.value is NormalClockComponent) {
            _themeComponent.value = (DigitalClockComponent())
            lastUsedTheme = "digital"
        } else {
            _themeComponent.value = (NormalClockComponent())
            lastUsedTheme = "normal"
        }
    }
}