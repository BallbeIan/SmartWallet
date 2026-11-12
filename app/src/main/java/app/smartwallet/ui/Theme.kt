package app.smartwallet.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Green = Color(0xFF24D08F)
val Amber = Color(0xFFFFB74D)
val Expense = Color(0xFFFF5C7A)
val Blue = Color(0xFF2D8CFF)
val Teal = Color(0xFF19C3B1)
private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF131A2B)
private val PanelHigh = Color(0xFF1A2236)
private val Line = Color(0xFF232C42)

private val Scheme = darkColorScheme(
    primary = Green, onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF123B38), onPrimaryContainer = Color(0xFF9FF0CF),
    secondaryContainer = Color(0xFF1A2236), onSecondaryContainer = Color(0xFFE3EAF6),
    background = Bg, onBackground = Color(0xFFEAF0FA),
    surface = Bg, onSurface = Color(0xFFEAF0FA),
    surfaceVariant = PanelHigh, onSurfaceVariant = Color(0xFF8E9BB5),
    surfaceContainerLowest = Bg, surfaceContainerLow = Panel, surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh, surfaceContainerHighest = PanelHigh,
    outline = Color(0xFF55627C), outlineVariant = Line,
    error = Expense, onError = Color(0xFF3B0A0A),
)

private val Base = Typography()
private val Type = Typography(
    displaySmall = Base.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(fontSize = 15.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 13.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 12.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
)

/** Tabular figures keep amounts in a column aligned. */
val Num = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
