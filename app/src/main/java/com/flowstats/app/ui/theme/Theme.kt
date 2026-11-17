package com.flowstats.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF3769F5)
val Purple = Color(0xFF7B4DFF)
val Orange = Color(0xFFFF9D3D)
val Teal = Color(0xFF00BDB0)
val Slate = Color(0xFF6B7D8C)
val Track = Color(0xFFE6ECF5)
val ScreenBg = Color(0xFFF2F5FB)
val Ink = Color(0xFF17233D)
val SubText = Color(0xFF61708B)
val Success = Color(0xFF22C55E)
val Danger = Color(0xFFF2545B)
val SurfaceDark = Color(0xFF131C30)
val SurfaceDark2 = Color(0xFF0E1628)
val LineDark = Color(0xFF1E2B44)
val TextDark = Color(0xFFE8EEFB)
val SubDark = Color(0xFF94A3BD)

val HeroColors = listOf(Color(0xFF173A8F), Blue, Purple)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Teal,
    background = ScreenBg,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF7F9FD),
    onSurfaceVariant = SubText
)

private val DarkColors = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Teal,
    background = Color(0xFF0A1020),
    onBackground = TextDark,
    surface = SurfaceDark,
    onSurface = TextDark,
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = SubDark
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 10.sp)
)

@Composable
fun FlowStatsTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
