@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import dev.morin.sunrelay.R
import dev.morin.sunrelay.net.SystemScreens
import dev.morin.sunrelay.update.UpdateChecker

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { UpdateChecker.installedVersion(context) }
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { SystemScreens.url(context, UpdateChecker.REPOSITORY_URL) }) {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = "Source code on GitHub")
                    }
                },
            )
        },
    ) { padding ->
        ScrollingPage(padding) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShapedBadge(MaterialShapes.Sunny, cs.primaryContainer, HERO_SIZE, rotating = true) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "SunRelay logo", modifier = Modifier.size(HERO_SIZE).scale(LOGO_ART_SCALE))
                }
                Text("SunRelay", style = MaterialTheme.typography.headlineLarge)
                Text(
                    buildAnnotatedString {
                        append("Version $version · Made with ")
                        withStyle(SpanStyle(color = HeartRed)) { append("♥") }
                        append(" by $AUTHOR")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                "Thank you for using open source software. Wishing you smooth streams and good days ahead.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            DonationCard(context)
            Text(
                TRADEMARK_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun DonationCard(context: Context) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CatPaw(Color.White, PAW_SIZE)
            Text("Support SunRelay", style = MaterialTheme.typography.titleLarge)
            Text(
                "No coffee needed. If SunRelay is useful to you, please donate whatever amount you can " +
                    "to an animal shelter near you. That's the best way to support this project.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = { findShelter(context) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
            ) { Text("Find a shelter near me", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun CatPaw(padColor: Color, size: Dp) {
    Box(Modifier.size(size)) {
        Canvas(Modifier.matchParentSize()) {
            TOES.forEach { toe ->
                val center = Offset(toe.x * this.size.width, toe.y * this.size.height)
                rotate(toe.angle, center) {
                    drawOval(
                        padColor,
                        topLeft = Offset(center.x - toe.width * this.size.width / 2, center.y - toe.height * this.size.height / 2),
                        size = Size(toe.width * this.size.width, toe.height * this.size.height),
                    )
                }
            }
            drawPath(mainPad(this.size), padColor)
        }
        Icon(
            painterResource(R.drawable.ic_heart),
            contentDescription = null,
            tint = HeartRed,
            modifier = Modifier
                .size(size * 0.26f)
                .align(BiasAlignment(0f, 0.42f)),
        )
    }
}

private fun mainPad(size: Size): Path {
    fun x(f: Float) = f * size.width
    fun y(f: Float) = f * size.height
    return Path().apply {
        moveTo(x(0.5f), y(0.50f))
        cubicTo(x(0.62f), y(0.50f), x(0.70f), y(0.60f), x(0.77f), y(0.70f))
        cubicTo(x(0.84f), y(0.81f), x(0.79f), y(0.95f), x(0.66f), y(0.95f))
        cubicTo(x(0.58f), y(0.95f), x(0.56f), y(0.91f), x(0.5f), y(0.91f))
        cubicTo(x(0.44f), y(0.91f), x(0.42f), y(0.95f), x(0.34f), y(0.95f))
        cubicTo(x(0.21f), y(0.95f), x(0.16f), y(0.81f), x(0.23f), y(0.70f))
        cubicTo(x(0.30f), y(0.60f), x(0.38f), y(0.50f), x(0.5f), y(0.50f))
        close()
    }
}

private class Toe(val x: Float, val y: Float, val width: Float, val height: Float, val angle: Float)

private val TOES = listOf(
    Toe(0.16f, 0.43f, 0.17f, 0.23f, -28f),
    Toe(0.37f, 0.22f, 0.19f, 0.25f, -9f),
    Toe(0.63f, 0.22f, 0.19f, 0.25f, 9f),
    Toe(0.84f, 0.43f, 0.17f, 0.23f, 28f),
)

private fun findShelter(context: Context) {
    val maps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(SHELTER_QUERY)}"))
    try {
        context.startActivity(maps)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/${Uri.encode(SHELTER_QUERY)}")))
    }
}

private const val TRADEMARK_NOTICE =
    "Product names and logos are trademarks of their owners. SunRelay is independent and not endorsed by them."
private const val AUTHOR = "git-morin"
private const val SHELTER_QUERY = "animal shelter"
private val HERO_SIZE = 96.dp
private val PAW_SIZE = 96.dp
