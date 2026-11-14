package app.smartwallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The big bold title at the top of each screen, with room for buttons on the right. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.displayMedium, maxLines = 1)
        trailing()
    }
}

/** "‹ Home": the way back from a pushed screen. */
@Composable
fun BackButton(label: String, onClick: () -> Unit) {
    Row(Modifier.padding(start = 6.dp, top = 4.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ChevronLeft, null, tint = Green, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Green)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) =
    Text(text.uppercase(), modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 7.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** An inset rounded card: one group of a grouped list. */
@Composable
fun Grouped(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(content = content)
    }

/** The hairline between rows, inset from the left. */
@Composable
fun Hairline(startIndent: Dp = 16.dp) =
    HorizontalDivider(Modifier.padding(start = startIndent), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

/** A coloured rounded square with a white symbol, used as an icon badge. */
@Composable
fun Squircle(icon: ImageVector, color: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))

/** The green switch. */
@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val off = if (AppTheme.isDark) Color(0xFF39393D) else Color(0xFFE9E9EA)
    Switch(
        checked, onChange, modifier, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = Green, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = off, uncheckedBorderColor = Color.Transparent,
            disabledCheckedTrackColor = Green.copy(alpha = 0.4f), disabledUncheckedTrackColor = off.copy(alpha = 0.6f),
            disabledCheckedThumbColor = Color.White, disabledUncheckedThumbColor = Color.White,
        ),
    )
}

/** Readable text for a symbol or letter drawn on top of [c]. */
fun onColor(c: Color): Color = if (c.luminance() > 0.55f) Color(0xFF1C1C1E) else Color.White

/** A coloured rounded square holding one letter, used where a category or account has no symbol of its own. */
@Composable
fun LetterBadge(letter: String, color: Color, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color), contentAlignment = Alignment.Center) {
        Text(letter.take(1).uppercase(), color = onColor(color), style = MaterialTheme.typography.titleMedium)
    }
}

/** One line in a grouped list: optional badge, a title with an optional second line, and something on the right. */
@Composable
fun ListRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

/** A thin rounded progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(color))
    }
}

/** "‹ November 2026 ›" in a small grey capsule. */
@Composable
fun MonthPill(month: java.time.YearMonth, onShift: (Long) -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ChevronLeft, "Previous month", tint = Green, modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onShift(-1) }.padding(4.dp))
        Text(month.label(), Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", tint = Green, modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onShift(1) }.padding(4.dp))
    }
}
