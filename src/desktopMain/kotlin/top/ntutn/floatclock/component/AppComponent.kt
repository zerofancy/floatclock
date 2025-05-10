package top.ntutn.floatclock.component

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import top.ntutn.floatclock.decompose.MutableValue
import top.ntutn.floatclock.decompose.Value
import top.ntutn.floatclock.storage.DataStoreFactory
import java.awt.Dimension

class AppComponent {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val dataStore = DataStoreFactory().createThemeDataStore()

    private val _floatWindowSize = MutableValue(Dimension(200, 100))
    val floatWindowSize: Value<Dimension> get() = _floatWindowSize
    private val _themeComponent = MutableValue<IClockComponent>(DigitalClockComponent(dataStore))
    val themeComponent: Value<IClockComponent> get() = _themeComponent

    init {
        dataStore.themeData()
            .map { it.theme }
            .distinctUntilChanged()
            .onEach {
                val originComponent = _themeComponent.value
                _themeComponent.value = if (it == "digital") {
                    DigitalClockComponent(dataStore)
                } else {
                    NormalClockComponent(dataStore)
                }
                originComponent.destroy()
            }.launchIn(coroutineScope)
    }

    fun floatWindowLayout(width: Int, height: Int) {
        if (floatWindowSize.value.width != width || floatWindowSize.value.height != height) {
            _floatWindowSize.value = (Dimension(width, height))
        }
    }

    fun changeTheme() {
        coroutineScope.launch {
            dataStore.updateTheme()
        }
    }

    fun destroy() {
        coroutineScope.cancel()
        _themeComponent.value.destroy()
    }
}