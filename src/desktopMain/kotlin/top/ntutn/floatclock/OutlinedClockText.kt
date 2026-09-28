package top.ntutn.floatclock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** One layout and origin for both passes, including fallback glyphs such as speed arrows. */
@Composable
internal fun OutlinedClockText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    fontFamily: FontFamily?,
    outlineColor: Color,
    showOutline: Boolean,
    outlineOutsideWidth: Dp,
) {
    val measurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(fontSize = fontSize, fontFamily = fontFamily)
    val layout = measurer.measure(
        text = AnnotatedString(text),
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
    val density = LocalDensity.current
    val strokeWidth = with(density) { outlineOutsideWidth.toPx() * 2 }
    val inset = if (showOutline) with(density) {
        ceil(outlineOutsideWidth.toPx() + 1.dp.toPx())
    } else 0f
    val origin = Offset(inset, inset)
    Canvas(
        Modifier.requiredSize(
            with(density) { (layout.size.width + 2 * inset).toDp() },
            with(density) { (layout.size.height + 2 * inset).toDp() },
        ).semantics { this.text = AnnotatedString(text) },
    ) {
        if (showOutline) {
            drawText(layout, outlineColor, origin, drawStyle = Stroke(strokeWidth, join = StrokeJoin.Round))
        }
        drawText(layout, color, origin, drawStyle = Fill)
    }
}
