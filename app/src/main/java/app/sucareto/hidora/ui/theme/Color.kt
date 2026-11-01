package app.sucareto.hidora.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    secondary = Color(0xFF535F70),
    tertiary = Color(0xFF6B5778)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCAFF),
    secondary = Color(0xFFBBC7DB),
    tertiary = Color(0xFFD6BEE4)
)

data class AppExtraColors(
    val keyNormal: Color,
    val keyFunction: Color,
    val keyHeld: Color,
    val keyLocked: Color,
    val trackpadSurface: Color,
    val trackpadBorder: Color,
    val onKeyNormal: Color,
    val onKeyFunction: Color,
    val onKeyHeld: Color,
    val onKeyLocked: Color
)

val LightExtraColors = AppExtraColors(
    keyNormal = Color(0xFFE3E7EC),
    keyFunction = Color(0xFFC8CED6),
    keyHeld = Color(0xFF4C9EEB),
    keyLocked = Color(0xFFD3455B),
    trackpadSurface = Color(0xFFE3E7EC),
    trackpadBorder = Color(0xFFB7BEC6),
    onKeyNormal = Color(0xFF1A1C1E),
    onKeyFunction = Color(0xFF1A1C1E),
    onKeyHeld = Color(0xFF1A1C1E),
    onKeyLocked = Color(0xFF1A1C1E)
)

val DarkExtraColors = AppExtraColors(
    keyNormal = Color(0xFF3A3F44),
    keyFunction = Color(0xFF2A2E32),
    keyHeld = Color(0xFF3D7FBF),
    keyLocked = Color(0xFFA6394A),
    trackpadSurface = Color(0xFF3A3F44),
    trackpadBorder = Color(0xFF565D64),
    onKeyNormal = Color(0xFFE2E2E6),
    onKeyFunction = Color(0xFFE2E2E6),
    onKeyHeld = Color(0xFF1A1C1E),
    onKeyLocked = Color(0xFFE2E2E6)
)

val LocalAppExtraColors = staticCompositionLocalOf<AppExtraColors> { error("LocalAppExtraColors requires AppTheme") }
