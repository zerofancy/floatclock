// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.system.exitProcess

val sdf = SimpleDateFormat("hh:mm")

@Composable
@Preview
fun Timer() {
    var text by remember { mutableStateOf("88:88") }
    Text(text = text, fontSize = 48.sp, overflow = TextOverflow.Visible, maxLines = 1, color = Color.Red)
    LaunchedEffect(null) {
        while (true) {
            text = sdf.format(System.currentTimeMillis())
            delay(1000L)
        }
    }
}

fun main(vararg args: String) = SwingUtilities.invokeLater {
    ComposeWindow().apply {

        // 计算预期窗口大小
        // https://stackoverflow.com/a/8267630
        val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).graphics
        val font = Font(g.font.name, g.font.style, 48)
        val bounds = font.getStringBounds("88: 88", g.fontMetrics.fontRenderContext)
        size = Dimension(bounds.width.roundToInt(), bounds.height.roundToInt())

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
            WindowDraggableArea(modifier = Modifier.fillMaxSize()) { }
            Timer()

            LaunchedEffect(null) {
                if (args.contains("--release")) {
                    return@LaunchedEffect
                }
                // 防止debug时配错参数导致无法退出
                delay(10_000)
                exitProcess(0)
            }
        }
        // 置顶显示
        isAlwaysOnTop = true
        isVisible = true
    }
}