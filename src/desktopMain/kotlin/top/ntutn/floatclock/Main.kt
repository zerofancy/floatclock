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
import java.awt.*
import java.awt.event.*
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities


object App

@OptIn(DelicateCoroutinesApi::class)
fun main(vararg args: String) {
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
        showFloatingWindow(appIconImage, appComponent) {
            aboutDialogFactory().isVisible = true
        }
    }
}

private fun showFloatingWindow(appIconImage: BufferedImage, appComponent: AppComponent, showAboutWindowAction: () -> Unit) {
    JDialog().apply {
        val popupMenu = ContextMenu(
            themeAction = appComponent::changeTheme,
            colorEditAction = {
                appComponent.themeComponent.value.showEditColorPanel()
            },
            colorAction = {
                appComponent.themeComponent.value.changeColor(it)
            },
            aboutAction = showAboutWindowAction,
            exitAction = ::dispose
        )

        plantTrayIcon(appIconImage, appComponent, popupMenu)

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

private fun JDialog.plantTrayIcon(
    appIconImage: BufferedImage,
    appComponent: AppComponent,
    popupMenu: ContextMenu
) {
    if (SystemTray.isSupported()) {
        val trayIcon = TrayIcon(appIconImage, "简易桌面悬浮时钟")
        trayIcon.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                e ?: return
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // 左键点击
                    appComponent.themeComponent.value.changeColor(null)
                    return
                }
                val pointerLocation = MouseInfo.getPointerInfo().location
                popupMenu.location = pointerLocation
                popupMenu.invoker = popupMenu
                popupMenu.isVisible = true
            }
        })
        trayIcon.isImageAutoSize = true
        SystemTray.getSystemTray().add(trayIcon)

        SwingUtilities.invokeLater {
            val tray = SystemTray.getSystemTray()
            val trayIconSize = tray.trayIconSize
            trayIcon.image = appIconImage.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_DEFAULT)
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                super.windowClosed(e)
                SystemTray.getSystemTray().remove(trayIcon)
            }
        })
    }
}