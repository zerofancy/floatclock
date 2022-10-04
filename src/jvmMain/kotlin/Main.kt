// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import androidx.compose.desktop.ui.tooling.preview.Preview
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
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.ntutn.floatclock.BuildConfig
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.net.URI
import java.text.SimpleDateFormat
import javax.swing.JFrame
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.system.exitProcess

val sdf = SimpleDateFormat("hh:mm")

@Composable
@Preview
fun Clock(color: Color = Color.Red) {
    var text by remember { mutableStateOf("88:88") }
    Text(text = text, fontSize = 48.sp, overflow = TextOverflow.Visible, maxLines = 1, color = color)
    LaunchedEffect(null) {
        while (true) {
            text = sdf.format(System.currentTimeMillis())
            delay(1000L)
        }
    }
}

@Composable
fun ApplicationScope.TrayBlock(changeColor: () -> Unit, showAbout: () -> Unit, exit: () -> Unit) {
    val trayState = rememberTrayState()
    Tray(
        state = trayState,
        icon = painterResource("clock.png"),
        menu = {
            Item("Change Color", onClick = changeColor)
            Item("About", onClick = showAbout)
            Item("Exit", onClick = exit)
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

            // 计算预期窗口大小
            // https://stackoverflow.com/a/8267630
            val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
            val font = Font(g.font.name, g.font.style, 48)
            val bounds = font.getStringBounds("88: 88", g.fontMetrics.fontRenderContext)
            val fixedSize = { Dimension(bounds.width.roundToInt(), bounds.height.roundToInt()) }
            val initialSize = fixedSize()
            size = fixedSize()
            maximumSize = fixedSize()
            minimumSize = fixedSize()

            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            // 显示在所有桌面
            type = java.awt.Window.Type.POPUP
            // 无标题透明 不自动抢夺焦点
            isUndecorated = true
            isTransparent = true
            isAutoRequestFocus = false

            // 自动显示在屏幕右下角
            val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
            setLocation(screenSize.width - size.width * 2, screenSize.height - size.height * 2)
            setContent {
                var color by remember { mutableStateOf(randomColor()) }
                var isAboutShowing by remember { mutableStateOf(false) }

                WindowDraggableArea(modifier = Modifier.fillMaxSize()) { }
                Clock(color)

                TrayBlock(changeColor = {
                    color = randomColor()
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

                LaunchedEffect(null) {
                    launch {
                        while (true) {
                            delay(1000L)
                            if (!isAlive) {
                                return@launch
                            }
                            // 防止一些极端情况下窗口大小被改变
                            if (size.height != initialSize.height || size.width != initialSize.width) {
                                size = fixedSize()
                            }
                        }
                    }
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

/**
 * 拍脑袋想的随机颜色算法
 */
@OptIn(ExperimentalGraphicsApi::class)
private fun randomColor(): Color {
    val h = (0..360).random().toFloat()
    val s = Random.nextFloat()
    var l = Random.nextFloat()
    while (l < 0.3f || l > 0.8) {
        l = Random.nextFloat()
    }
    return Color.hsl(h, s, l)
}