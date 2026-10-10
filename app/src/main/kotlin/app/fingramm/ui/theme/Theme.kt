package app.fingramm.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B6E5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA7F1DC),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4A635B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8DE),
    onSecondaryContainer = Color(0xFF06201A),
    tertiary = Color(0xFF3F6375),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC3E8FD),
    onTertiaryContainer = Color(0xFF001F2B),
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5C0),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    onPrimaryContainer = Color(0xFFA7F1DC),
    secondary = Color(0xFFB1CCC3),
    onSecondary = Color(0xFF1C352E),
    secondaryContainer = Color(0xFF334B44),
    onSecondaryContainer = Color(0xFFCCE8DE),
    tertiary = Color(0xFFA7CCE0),
    onTertiary = Color(0xFF083546),
    tertiaryContainer = Color(0xFF254C5D),
    onTertiaryContainer = Color(0xFFC3E8FD),
    background = Color(0xFF191C1B),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Семантические цвета статусов показателей. Статус всегда дублируется текстом и значком. */
@Immutable
data class StatusColors(
    val good: Color,
    val onGood: Color,
    val goodContainer: Color,
    val attention: Color,
    val onAttention: Color,
    val attentionContainer: Color,
    val risk: Color,
    val onRisk: Color,
    val riskContainer: Color,
    val income: Color,
    val expense: Color,
)

private val LightStatusColors = StatusColors(
    good = Color(0xFF1B6E3A),
    onGood = Color(0xFF0B3A1F),
    goodContainer = Color(0xFFD3F0DB),
    attention = Color(0xFF8A5100),
    onAttention = Color(0xFF4A2B00),
    attentionContainer = Color(0xFFFFE6BD),
    risk = Color(0xFFB3261E),
    onRisk = Color(0xFF5C0F0A),
    riskContainer = Color(0xFFF9DEDC),
    income = Color(0xFF1B6E3A),
    expense = Color(0xFFB3261E),
)

private val DarkStatusColors = StatusColors(
    good = Color(0xFF8FD9A5),
    onGood = Color(0xFFC9F2D4),
    goodContainer = Color(0xFF0F3A20),
    attention = Color(0xFFFFC857),
    onAttention = Color(0xFFFFE2A8),
    attentionContainer = Color(0xFF4A3000),
    risk = Color(0xFFFFB4AB),
    onRisk = Color(0xFFFFDAD6),
    riskContainer = Color(0xFF601410),
    income = Color(0xFF8FD9A5),
    expense = Color(0xFFFFB4AB),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Палитра для диаграмм: различима по тону и яркости, достаточный контраст с фоном в обеих темах. */
object ChartPalette {
    val light = listOf(
        Color(0xFF1B6E5A),
        Color(0xFF3F6375),
        Color(0xFF8A5100),
        Color(0xFF7B3F8C),
        Color(0xFFA23B4A),
        Color(0xFF5A6E1B),
        Color(0xFF1E5BA8),
        Color(0xFF6B5B4E),
    )
    val dark = listOf(
        Color(0xFF8BD5C0),
        Color(0xFFA7CCE0),
        Color(0xFFFFC857),
        Color(0xFFDDB1EA),
        Color(0xFFFFB1BB),
        Color(0xFFC7DD7F),
        Color(0xFFA9C7FF),
        Color(0xFFD7C4B7),
    )
}

@Composable
fun FingrammTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
