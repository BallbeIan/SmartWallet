package app.smartwallet.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dark, light, or follow the phone. Compose state, so changing it redraws the whole app. */
object AppTheme {
    enum class Mode(val label: String) { SYSTEM("Same as phone"), LIGHT("Light"), DARK("Dark") }

    var mode by mutableStateOf(Mode.DARK)
        private set
    private var systemDark by mutableStateOf(true)

    val isDark: Boolean get() = when (mode) { Mode.SYSTEM -> systemDark; Mode.LIGHT -> false; Mode.DARK -> true }

    fun load(c: Context) {
        mode = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", null)
            ?.let { name -> Mode.entries.firstOrNull { it.name == name } } ?: Mode.DARK
        systemDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun set(c: Context, m: Mode) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", m.name).apply()
        mode = m
    }
}

// Accent colours need a darker shade on a light background to stay readable. These read the theme state, so they update live.
val Green: Color get() = if (AppTheme.isDark) Color(0xFF24D08F) else Color(0xFF0B9B69)
val Amber: Color get() = if (AppTheme.isDark) Color(0xFFFFB74D) else Color(0xFFC77700)
val Expense: Color get() = if (AppTheme.isDark) Color(0xFFFF5C7A) else Color(0xFFD6254B)
val Blue: Color get() = if (AppTheme.isDark) Color(0xFF2D8CFF) else Color(0xFF1B6FE0)
val Teal: Color get() = if (AppTheme.isDark) Color(0xFF19C3B1) else Color(0xFF0C9A8E)

private val Bg = Color(0xFF090D18)
private val Panel = Color(0xFF131A2B)
private val PanelHigh = Color(0xFF1A2236)
private val Line = Color(0xFF232C42)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF24D08F), onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF123B38), onPrimaryContainer = Color(0xFF9FF0CF),
    secondaryContainer = Color(0xFF1A2236), onSecondaryContainer = Color(0xFFE3EAF6),
    background = Bg, onBackground = Color(0xFFEAF0FA),
    surface = Bg, onSurface = Color(0xFFEAF0FA),
    surfaceVariant = PanelHigh, onSurfaceVariant = Color(0xFF8E9BB5),
    surfaceContainerLowest = Bg, surfaceContainerLow = Panel, surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh, surfaceContainerHighest = PanelHigh,
    outline = Color(0xFF55627C), outlineVariant = Line,
    error = Color(0xFFFF5C7A), onError = Color(0xFF3B0A0A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0B9B69), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F4E5), onPrimaryContainer = Color(0xFF053D2B),
    secondaryContainer = Color(0xFFE3E9F4), onSecondaryContainer = Color(0xFF16213A),
    background = Color(0xFFF5F7FB), onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF5F7FB), onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EDF5), onSurfaceVariant = Color(0xFF55627C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF1F8), surfaceContainerHighest = Color(0xFFEDF1F8),
    outline = Color(0xFF8793A8), outlineVariant = Color(0xFFDCE3EE),
    error = Color(0xFFD6254B), onError = Color.White,
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
        colorScheme = if (AppTheme.isDark) DarkScheme else LightScheme,
        typography = Type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
