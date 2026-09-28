package top.ntutn.floatclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutlinedClockTextTest {
    private val digitalFont = FontFamily(Font(
        "digital-7-test",
        checkNotNull(javaClass.getResourceAsStream("/digital-7.ttf")).use { it.readBytes() },
    ))

    private fun render(
        value: String,
        font: FontFamily?,
        scale: Float,
        outlined: Boolean,
        outline: Color = Color.White,
        originalText: Boolean = false,
        small: Boolean = false,
    ): BufferedImage {
        var result: BufferedImage? = null
        SwingUtilities.invokeAndWait {
            val scene = ImageComposeScene(900, 180, Density(scale)) {
                Box(Modifier.padding(8.dp)) {
                    if (originalText) {
                        Text(value, fontSize = if (small) 14.sp else 48.sp, color = Color.Red, fontFamily = font, maxLines = 1)
                    } else {
                        OutlinedClockText(
                            value, if (small) 14.sp else 48.sp, Color.Red, font,
                            outline, outlined, if (small) 0.5.dp else 1.dp,
                        )
                    }
                }
            }
            try {
                scene.render().use { image ->
                    image.encodeToData()!!.use { data ->
                        result = ImageIO.read(ByteArrayInputStream(data.bytes))
                    }
                }
            } finally {
                scene.close()
            }
        }
        return checkNotNull(result)
    }

    private fun BufferedImage.pixels(): IntArray = getRGB(0, 0, width, height, null, 0, width)

    @Test
    fun disabledOutlineMatchesExistingText() {
        for (font in listOf(null, digitalFont)) {
            for (small in listOf(false, true)) {
                val value = if (small) "↓ 128 KB/s   ↑ 9 MB/s" else "23:59"
                val original = render(value, font, 1f, false, originalText = true, small = small)
                val replacement = render(value, font, 1f, false, small = small)
                assertTrue(original.pixels().contentEquals(replacement.pixels()), "Disabled outline changed text rendering")
            }
        }
    }

    @Test
    fun outlineAddsVisiblePixelsAndPreservesFillAcrossDensities() {
        val reports = File("build/reports/outline").apply { mkdirs() }
        for ((fontName, font) in listOf("normal" to null, "digital" to digitalFont)) {
            for (scale in listOf(1f, 1.25f, 1.5f, 2f)) {
                for ((name, outline) in listOf("white" to Color.White, "black" to Color.Black)) {
                    for (small in listOf(false, true)) {
                        val value = if (small) "↓ 128 KB/s   ↑ 9 MB/s" else "00:00 11:11"
                        val plain = render(value, font, scale, false, small = small)
                        val stroked = render(value, font, scale, true, outline, small = small)
                        val pixels = stroked.pixels()
                        assertTrue(pixels.count { it ushr 24 > 0 } > plain.pixels().count { it ushr 24 > 0 })
                        assertTrue(pixels.any { it == 0xffff0000.toInt() }, "Foreground fill missing")
                        // The image boundary must remain transparent on every side.
                        for (x in 0 until stroked.width) {
                            assertEquals(0, stroked.getRGB(x, 0) ushr 24)
                            assertEquals(0, stroked.getRGB(x, stroked.height - 1) ushr 24)
                        }
                        for (y in 0 until stroked.height) {
                            assertEquals(0, stroked.getRGB(0, y) ushr 24)
                            assertEquals(0, stroked.getRGB(stroked.width - 1, y) ushr 24)
                        }
                        if (scale == 2f) ImageIO.write(stroked, "png", File(reports, "$fontName-$name-${if (small) "speed" else "time"}.png"))
                    }
                }
            }
        }
    }
}
