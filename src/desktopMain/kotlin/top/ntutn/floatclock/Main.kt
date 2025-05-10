package top.ntutn.floatclock// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import androidx.compose.ui.awt.ComposeWindow
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import top.ntutn.floatclock.clock.ClockPanel
import top.ntutn.floatclock.clock.ContextMenu
import top.ntutn.floatclock.clock.MotionPanel
import top.ntutn.floatclock.component.AppComponent
import top.ntutn.floatclock.storage.ConfigUtil
import java.awt.*
import java.awt.event.*
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JFrame

object App

@OptIn(DelicateCoroutinesApi::class)
fun main(vararg args: String) {
    ConfigUtil.init()
    val appComponent = AppComponent()
    // 图标太大设置后会不起效
    val appIconImage = ImageIO.read(App.javaClass.classLoader.getResourceAsStream("clock_small.png"))
    GlobalScope.launch(Dispatchers.Main) {
        val aboutDialogFactory = {
            ComposeWindow().also {
                it.setContent {
                    AboutContent()
                }
                it.size = Dimension(400, 300)
                it.setLocationRelativeTo(null)

                it.iconImage = appIconImage
                it.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            }
        }
        showFloatingWindow(appComponent) {
            aboutDialogFactory().isVisible = true
        }
    }
}

fun showFloatingWindow(appComponent: AppComponent, showAboutWindowAction: () -> Unit) {
    JDialog().apply {
        val popupMenu = ContextMenu(
            themeAction = appComponent::changeTheme,
            colorEditAction = appComponent.themeComponent.value::showEditColorPanel,
            colorAction = appComponent.themeComponent.value::changeColor,
            aboutAction = showAboutWindowAction,
            exitAction = ::dispose
        )

        appComponent.floatWindowSize.subscribe {
            size = it
        }

        defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        // 显示在所有桌面
        type = Window.Type.UTILITY
        // 无标题透明 不自动抢夺焦点
        isUndecorated = true
        background = Color(
            255, 255, 255, if (SystemUtils.IS_OS_WINDOWS) {
                1 // Windows设置为0会出现鼠标穿透
            } else {
                0
            }
        )
        isAutoRequestFocus = false

        // 自动显示在屏幕右下角
        val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
        setLocation(screenSize.width - size.width * 2, screenSize.height - size.height * 2)

        add(MotionPanel(this) {
            popupMenu.show(this, it.x, it.y)
        })
        add(ClockPanel(appComponent))
        // 置顶显示
        isAlwaysOnTop = true
        isVisible = true
    }
}