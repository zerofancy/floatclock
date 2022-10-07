package top.ntutn.floatclock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import top.ntutn.floatclock.util.DesktopBrowse
import java.net.URI

@Composable
fun AboutContent() {
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
                GlobalScope.launch(Dispatchers.Default) {
                    DesktopBrowse.browse(URI.create(url))
                }
            })
        }
}