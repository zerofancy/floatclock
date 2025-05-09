package top.ntutn.floatclock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import top.ntutn.floatclock.generated.resources.Res
import top.ntutn.floatclock.generated.resources.clock

@Composable
fun AboutContent() {
        Column(modifier = Modifier.fillMaxSize()) {
            val url = "https://github.com/zerofancy/floatclock"
            val modifier = Modifier.align(Alignment.CenterHorizontally)

            Spacer(modifier.height(16.dp))
            Image(
                painter = painterResource(Res.drawable.clock),
                contentDescription = null,
                modifier = modifier.size(64.dp, 64.dp)
            )
            Spacer(modifier.height(8.dp))
            Text("${BuildConfig.APP_NAME} ${BuildConfig.APP_VERSION}", modifier = modifier)
            Spacer(modifier.height(8.dp))
            Text(buildAnnotatedString {
                withLink(LinkAnnotation.Url(url)) {
                    append(url)
                }
            }, modifier = modifier)
        }
}