package top.ntutn.floatclock// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontLoader
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.lang3.SystemUtils
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.Window
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.JFrame
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.system.exitProcess

@Composable
fun ApplicationScope.TrayBlock(changeTheme: ()->Unit, changeColor: () -> Unit, showAbout: () -> Unit, exit: () -> Unit) {
    val trayState = rememberTrayState()
    Tray(
        state = trayState,
        icon = painterResource("clock.png"),
        menu = {
            Item("更换主题", onClick = changeTheme)
            Item("切换颜色", onClick = changeColor)
            Item("关于", onClick = showAbout)
            Item("退出", onClick = exit)
        },
        onAction = changeColor
    )
}

@Composable
fun AboutDialog(onClose: () -> Unit) {
    Dialog(onCloseRequest = onClose, title = "关于") {
        Column(modifier = Modifier.fillMaxSize()) {
            val url = "https://github.com/zerofancy/floatclock"
            val modifier = Modifier.align(Alignment.CenterHorizontally)

            Spacer(modifier.height(16.dp))
            Image(
                painter = painterResource("clock.png"),
                contentDescription = null,
                modifier = modifier.size(64.dp, 64.dp)
            )
            Spacer(modifier.height(8.dp))
            Text("kotlin-float-clock ${BuildConfig.version}", modifier = modifier)
            Spacer(modifier.height(8.dp))
            ClickableText(buildAnnotatedString {
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(style = SpanStyle(color = Color.Blue, fontWeight = FontWeight.Bold)) {
                    append(url)
                }
                pop()
            }, modifier = modifier, onClick = {
                onClose()
                GlobalScope.launch(Dispatchers.Default) {
                    DesktopBrowse.browse(URI.create(url))
                }
            })
        }
    }
}

fun main(vararg args: String) {
    var isAlive = true
    GlobalScope.launchApplication {
        ComposeWindow().apply {
            size = Dimension(100, 100)

            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            // 显示在所有桌面
            type = if (SystemUtils.IS_OS_WINDOWS) {
                Window.Type.UTILITY
            } else {
                Window.Type.POPUP
            }
            // 无标题透明 不自动抢夺焦点
            isUndecorated = true
            isTransparent = false
            isAutoRequestFocus = false

            // 自动显示在屏幕右下角
            val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
            setLocation(screenSize.width - size.width * 2, screenSize.height - size.height * 2)
            setContent {
                val density = LocalDensity.current
                val resourceLoader = LocalFontLoader.current
                var component: ClockComponent by remember { mutableStateOf(DigitalClockComponent(density, resourceLoader)) }
                var isAboutShowing by remember { mutableStateOf(false) }

                WindowDraggableArea(modifier = Modifier.fillMaxSize()) { }
                val currentComponent = component
                if (currentComponent is DigitalClockComponent) {
                    DigitalClock(currentComponent)
                } else if (currentComponent is NormalClockComponent) {
                    NormalClock(currentComponent)
                }

                TrayBlock(changeTheme = {
                    component = if (component is DigitalClockComponent) {
                        NormalClockComponent(density, resourceLoader)
                    } else {
                        DigitalClockComponent(density, resourceLoader)
                    }
                }, changeColor = {
                    component.changeColor()
                }, showAbout = {
                    isAboutShowing = true
                }, exit = {
                    dispose()
                    exitApplication()
                    isAlive = false
                })
                if (isAboutShowing) {
                    AboutDialog(onClose = { isAboutShowing = false })
                }

                LaunchedEffect(component) {
                    val expectedSize = component.measure()
                    while (true) {
                        if (!isAlive) {
                            return@LaunchedEffect
                        }
                        // 防止一些极端情况下窗口大小被改变
                        if (size.height != expectedSize.height || size.width != expectedSize.width) {
                            size = Dimension(expectedSize.width, expectedSize.height)
                        }
                        delay(1000L)
                    }
                }

                LaunchedEffect(null) {
                    if (args.contains("--release")) {
                        return@LaunchedEffect
                    }
                    // 防止debug时配错参数导致无法退出
                    delay(60_000)
                    exitProcess(0)
                }
            }
            // 置顶显示
            isAlwaysOnTop = true
            isVisible = true
        }
    }
    thread {
        while (true) {
            Thread.sleep(1000)
            if (!isAlive) {
                break
            }
        }
    }.join()
}