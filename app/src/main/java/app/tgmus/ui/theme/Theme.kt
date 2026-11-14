package app.tgmus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF7C5CFF)
private val Background = Color(0xFF0E0F13)
private val Surface = Color(0xFF16181F)
private val SurfaceHigh = Color(0xFF1F222B)

private val TgmusColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B2356),
    onPrimaryContainer = Color(0xFFE4DDFF),
    secondary = Color(0xFF9E8CFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2B2356),
    onSecondaryContainer = Color(0xFFE4DDFF),
    background = Background,
    onBackground = Color(0xFFF2F2F5),
    surface = Background,
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFFA6A9B6),
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceHigh,
    surfaceContainerHighest = Color(0xFF282C37),
    surfaceContainerLow = Surface,
    outline = Color(0xFF3A3E4B),
    outlineVariant = Color(0xFF262A34),
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
)

/** Приложение всегда тёмное, как музыкальные плееры. */
@Composable
fun TgmusTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TgmusColors, content = content)
}
