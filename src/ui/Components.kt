@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.morin.sunrelay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import dev.morin.sunrelay.R
import dev.morin.sunrelay.vpn.Vpn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.morin.sunrelay.config.RelayConfig

@Composable
fun ShapedBadge(
    polygon: RoundedPolygon,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    rotating: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val rotation = if (rotating) {
        val transition = rememberInfiniteTransition(label = "badge")
        val angle by transition.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(ROTATION_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "rotation",
        )
        angle
    } else {
        0f
    }
    val shape = polygon.toShape()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(shape)
                .background(color),
        )
        content()
    }
}

@Composable
fun AppVpnLogo(vpn: Vpn, size: Dp, modifier: Modifier = Modifier, greyscale: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val filter = if (greyscale) GREYSCALE else null
    val markSize = size * MARK_SCALE
    val logo = if (cs.surface.luminance() < 0.5f) vpn.logoOnDark else vpn.logo
    Box(modifier.size(size).semantics { contentDescription = "SunRelay over ${vpn.label}" }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_logo), contentDescription = null, colorFilter = filter, modifier = Modifier.matchParentSize().scale(LOGO_ART_SCALE))
        if (logo != null) {
            Image(painterResource(logo), contentDescription = null, colorFilter = filter, modifier = Modifier.offset(size * MARK_OFFSET_X, size * MARK_OFFSET_Y).size(markSize * vpn.logoScale))
        } else {
            Text(
                vpn.monogram,
                modifier = Modifier.offset(size * MARK_OFFSET_X, size * MARK_OFFSET_Y),
                color = cs.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = (markSize.value * if (vpn.monogram.length > 2) 0.28f else 0.38f).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun MoonBadge(color: Color, size: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val disc = Path().apply { addOval(Rect(Offset.Zero, this@Canvas.size)) }
            val bite = Path().apply {
                val r = this@Canvas.size.width * MOON_BITE_RADIUS
                val c = Offset(this@Canvas.size.width * MOON_BITE_X, this@Canvas.size.height * MOON_BITE_Y)
                addOval(Rect(c, r))
            }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), color)
        }
        content()
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun Section(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) { SectionHeader(title, subtitle) }
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp).rotate(rotation),
            )
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

class SegmentedRow(
    val headline: String,
    val supporting: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit = {},
    val leading: (@Composable () -> Unit)? = null,
    val trailing: (@Composable () -> Unit)? = null,
)

@Composable
fun SegmentedList(rows: List<SegmentedRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { index, row ->
            SegmentedListItem(
                selected = row.selected,
                onClick = row.onClick,
                shapes = ListItemDefaults.segmentedShapes(index, rows.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                leadingContent = row.leading,
                trailingContent = row.trailing,
                supportingContent = row.supporting?.let { { Text(it) } },
            ) { Text(row.headline) }
        }
    }
}

@Composable
fun StatusMark(ok: Boolean, size: Dp = 28.dp) {
    val cs = MaterialTheme.colorScheme
    ShapedBadge(
        polygon = if (ok) MaterialShapes.Cookie9Sided else MaterialShapes.Circle,
        color = if (ok) cs.primary else cs.errorContainer,
        size = size,
        modifier = Modifier.semantics { contentDescription = if (ok) "Done" else "Not done" },
    ) {
        Text(
            if (ok) "✓" else "!",
            color = if (ok) cs.onPrimary else cs.onErrorContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun StatusRow(ok: Boolean, label: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatusMark(ok)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (!ok && actionLabel != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun StepperItem(
    number: Int,
    title: String,
    done: Boolean,
    current: Boolean,
    summary: String?,
    isLast: Boolean,
    alwaysExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val badgeColor by animateColorAsState(
        when {
            done -> cs.primary
            current -> cs.primaryContainer
            else -> cs.surfaceContainerHighest
        },
        label = "badge",
    )
    val badgeContent = when {
        done -> cs.onPrimary
        current -> cs.onPrimaryContainer
        else -> cs.onSurfaceVariant
    }
    val expanded = current || (done && alwaysExpanded)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(STEP_BADGE)) {
            ShapedBadge(
                polygon = when {
                    done -> MaterialShapes.Cookie9Sided
                    current -> MaterialShapes.Sunny
                    else -> MaterialShapes.Circle
                },
                color = badgeColor,
                size = STEP_BADGE,
                modifier = Modifier.semantics { contentDescription = if (done) "Step $number done" else "Step $number" },
            ) {
                Text(if (done) "✓" else "$number", color = badgeContent, style = MaterialTheme.typography.titleMedium)
            }
            if (!isLast) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(if (done) cs.primary else cs.outlineVariant, MaterialTheme.shapes.extraSmall),
                )
            }
        }
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.heightIn(min = STEP_BADGE), verticalArrangement = Arrangement.Center) {
                Text(
                    title,
                    style = if (current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = if (done || current) cs.onSurface else cs.onSurfaceVariant,
                )
                if (done && !summary.isNullOrBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    color = if (current) cs.surfaceContainerHigh else cs.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                }
            }
        }
    }
}

@Composable
fun SunshineLoginFields(config: RelayConfig, onChange: (RelayConfig) -> Unit) {
    val login = config.login
    OutlinedTextField(
        login.user, { onChange(config.copy(login = login.copy(user = it))) }, Modifier.fillMaxWidth(),
        singleLine = true, label = { Text("Username") },
    )
    OutlinedTextField(
        login.password, { onChange(config.copy(login = login.copy(password = it))) }, Modifier.fillMaxWidth(),
        singleLine = true, label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
fun CredentialsNotice() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before you add it", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Stored only on this phone. The login is saved in SunRelay's private storage, " +
                    "is never backed up, and is only sent to your Sunshine host.",
                "Keep Sunshine off the open internet. Its web UI controls your PC, so it should only be " +
                    "reachable over your VPN. Don't port-forward any Sunshine ports.",
                "Why it helps: Sunshine remembers devices it paired with. When a device comes back through " +
                    "the relay at a new address, Moonlight can say \"Already paired\" yet never list the host. " +
                    "With the login, SunRelay can clear that stale pairing and take the new PIN right here, " +
                    "so you never get stuck.",
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

val HeartRed = Color(0xFFE53935)

private val STEP_BADGE = 40.dp
private const val MARK_SCALE = 0.46f
private const val MOON_BITE_RADIUS = 0.40f
private const val MOON_BITE_X = 0.78f
private const val MOON_BITE_Y = 0.30f
private val GREYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private const val MARK_OFFSET_X = 0.0f
private const val MARK_OFFSET_Y = -0.08f
const val LOGO_ART_SCALE = 1.2f
private const val ROTATION_MS = 12_000
