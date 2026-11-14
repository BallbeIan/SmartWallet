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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.smartwallet.R

/** Dark, light, or follow the phone (the default). Compose state, so changing it redraws the whole app. */
object AppTheme {
    enum class Mode(val label: String) { SYSTEM("Same as phone"), LIGHT("Light"), DARK("Dark") }

    var mode by mutableStateOf(Mode.SYSTEM)
        private set
    private var systemDark by mutableStateOf(true)

    val isDark: Boolean get() = when (mode) { Mode.SYSTEM -> systemDark; Mode.LIGHT -> false; Mode.DARK -> true }

    fun load(c: Context) {
        mode = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("theme", null)
            ?.let { name -> Mode.entries.firstOrNull { it.name == name } } ?: Mode.SYSTEM
        systemDark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun set(c: Context, m: Mode) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("theme", m.name).apply()
        mode = m
    }
}

// Apple's system colours. These read the theme state, so they update live when the theme changes.
val Green: Color get() = if (AppTheme.isDark) Color(0xFF30D158) else Color(0xFF2AAE4A)
/** Money coming in, as text: a deeper green on white so it stays readable. */
val IncomeText: Color get() = if (AppTheme.isDark) Color(0xFF30D158) else Color(0xFF248A3D)
val Expense: Color get() = if (AppTheme.isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
val Amber: Color get() = if (AppTheme.isDark) Color(0xFFFF9F0A) else Color(0xFFFF9500)
val Blue: Color get() = if (AppTheme.isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
val Teal: Color get() = if (AppTheme.isDark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
val Indigo: Color get() = if (AppTheme.isDark) Color(0xFF5E5CE6) else Color(0xFF5856D6)
val Purple: Color get() = if (AppTheme.isDark) Color(0xFFBF5AF2) else Color(0xFFAF52DE)
val Gray: Color get() = if (AppTheme.isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF30D158), onPrimary = Color.White,
    primaryContainer = Color(0xFF0F3A1E), onPrimaryContainer = Color(0xFF7DE29A),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
    background = Color(0xFF000000), onBackground = Color.White,
    surface = Color(0xFF000000), onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF1C1C1E), surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF636366), outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF453A), onError = Color.White,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2AAE4A), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F5E6), onPrimaryContainer = Color(0xFF14602A),
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color.Black,
    background = Color(0xFFF2F2F7), onBackground = Color.Black,
    surface = Color(0xFFF2F2F7), onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE5E5EA), surfaceContainerHighest = Color(0xFFD1D1D6),
    outline = Color(0xFFAEAEB2), outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30), onError = Color.White,
)

// Inter is the closest open font to Apple's San Francisco, and it is bundled so the app needs no internet to show it.
internal val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight, spacing: Double = 0.0, lineHeight: Int = (size * 1.25).toInt()) =
    TextStyle(fontFamily = Inter, fontWeight = weight, fontSize = size.sp, letterSpacing = spacing.sp, lineHeight = lineHeight.sp)

// Sizes follow Apple's text styles: Large Title 34, Title 2 22, Headline 17, Body 17, Subheadline 15, Footnote 13, Caption 12.
private val Type = Typography(
    displayLarge = style(57, FontWeight.Bold, -0.5), displayMedium = style(34, FontWeight.Bold, -0.4), displaySmall = style(44, FontWeight.Bold, -0.6),
    headlineLarge = style(32, FontWeight.Bold), headlineMedium = style(28, FontWeight.Bold, -0.3), headlineSmall = style(24, FontWeight.SemiBold),
    titleLarge = style(22, FontWeight.Bold, -0.2), titleMedium = style(17, FontWeight.SemiBold, -0.2), titleSmall = style(15, FontWeight.SemiBold),
    bodyLarge = style(17, FontWeight.Normal, -0.2), bodyMedium = style(15, FontWeight.Normal, -0.1), bodySmall = style(13, FontWeight.Normal),
    labelLarge = style(15, FontWeight.Medium), labelMedium = style(12, FontWeight.Medium), labelSmall = style(12, FontWeight.Normal, 0.4),
)

/** Tabular figures keep amounts in a column aligned. */
val Num = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun WalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (AppTheme.isDark) DarkScheme else LightScheme,
        typography = Type,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
