package cz.nicolsburg.boardflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppTheme(val label: String) {
    DARK("Dark (Amber)"),
    LIGHT("Light (Blue)")
}

// ── Dark palette ───────────────────────────────────────────────────────────
// Surfaces are neutral-cool graphite so gold accents pop by contrast.
// Containers are warm graphite — cohesive with gold without going brown.
private val AmberGold        = Color(0xFFFEB316)
private val AmberDark        = Color(0xFFC98C00)
private val AmberContainer   = Color(0xFF252420)  // warm graphite (primary/tertiary container)
private val AmberLight       = Color(0xFFFFDFA0)
private val AmberDeepDark    = Color(0xFF201E18)  // deep warm graphite (secondary container)

private val Background       = Color(0xFF131314)
private val Surface          = Color(0xFF1E1E1F)
private val SurfaceVariant   = Color(0xFF2A2A2B)
private val Outline          = Color(0xFF3D3D3E)
private val OutlineVariant   = Color(0xFF2E2E2F)

private val OnSurface        = Color(0xFFF0F0F0)
private val OnSurfaceMedium  = Color(0xFF9E9E9E)
private val OnAmber          = Color(0xFF131314)

private val ErrorRed         = Color(0xFFFF5252)
private val ErrorContainer   = Color(0xFF4D1010)
private val OnErrorContainer = Color(0xFFFFB4AB)

private val DarkColorScheme = darkColorScheme(
    primary              = AmberGold,
    onPrimary            = OnAmber,
    primaryContainer     = AmberContainer,
    onPrimaryContainer   = AmberLight,
    secondary            = AmberGold,
    onSecondary          = OnAmber,
    secondaryContainer   = AmberDeepDark,
    onSecondaryContainer = AmberLight,
    tertiary             = AmberDark,
    onTertiary           = OnAmber,
    tertiaryContainer    = AmberContainer,
    onTertiaryContainer  = AmberLight,
    background           = Background,
    onBackground         = OnSurface,
    surface              = Surface,
    onSurface            = OnSurface,
    surfaceVariant       = SurfaceVariant,
    onSurfaceVariant     = OnSurfaceMedium,

    surfaceContainerLowest  = Background,
    surfaceContainerLow     = Background,
    surfaceContainer        = Surface,
    surfaceContainerHigh    = SurfaceVariant,
    surfaceContainerHighest = Outline,
    surfaceBright           = SurfaceVariant,
    surfaceDim              = Background,

    outline              = Outline,
    outlineVariant       = OutlineVariant,
    error                = ErrorRed,
    onError              = OnAmber,
    errorContainer       = ErrorContainer,
    onErrorContainer     = OnErrorContainer,
    inverseSurface       = OnSurface,
    inverseOnSurface     = Background,
    inversePrimary       = AmberDark,
    scrim                = Color(0xFF000000),
)

// ── Light palette ───────────────────────────────────────────────────────────
// Polished light theme only

private val LightPrimary              = Color(0xFF2563EB)
private val LightPrimaryDark          = Color(0xFF1E40AF)
private val LightPrimaryContainer     = Color(0xFFDCEBFF)

private val LightSecondary            = Color(0xFF3B82F6)
private val LightSecondaryDark        = Color(0xFF1D4ED8)
private val LightSecondaryContainer   = Color(0xFFE7F0FF)

private val LightTertiary             = Color(0xFFCA8A04)
private val LightTertiaryDark         = Color(0xFF854D0E)
private val LightTertiaryContainer    = Color(0xFFFEF3C7)

private val LightBackground           = Color(0xFFF8FAFC)
private val LightSurface              = Color(0xFFFFFFFF)
private val LightSurfaceVariant       = Color(0xFFF1F5F9)

private val LightSurfaceLowest        = Color(0xFFFFFFFF)
private val LightSurfaceLow           = Color(0xFFFCFDFF)
private val LightSurfaceContainer     = Color(0xFFF8FAFC)
private val LightSurfaceHigh          = Color(0xFFF1F5F9)
private val LightSurfaceHighest       = Color(0xFFE8EEF6)

private val LightOutline              = Color(0xFFD7DEE7)
private val LightOutlineVariant       = Color(0xFFE5EAF1)

private val OnLightSurface            = Color(0xFF111827)
private val OnLightSurfaceVariant     = Color(0xFF4B5563)
private val OnLightMuted              = Color(0xFF6B7280)
private val OnAccent                  = Color(0xFFFFFFFF)

private val LightError                = Color(0xFFDC2626)
private val LightErrorContainer       = Color(0xFFFEE2E2)
private val OnLightErrorContainer     = Color(0xFF7F1D1D)

private val LightColorScheme = lightColorScheme(
    primary              = LightPrimary,
    onPrimary            = OnAccent,
    primaryContainer     = LightPrimaryContainer,
    onPrimaryContainer   = LightPrimaryDark,

    secondary            = LightSecondary,
    onSecondary          = OnAccent,
    secondaryContainer   = LightSecondaryContainer,
    onSecondaryContainer = LightSecondaryDark,

    tertiary             = LightTertiary,
    onTertiary           = OnAccent,
    tertiaryContainer    = LightTertiaryContainer,
    onTertiaryContainer  = LightTertiaryDark,

    background           = LightBackground,
    onBackground         = OnLightSurface,

    surface              = LightSurface,
    onSurface            = OnLightSurface,

    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = OnLightSurfaceVariant,

    surfaceContainerLowest  = LightSurfaceLowest,
    surfaceContainerLow     = LightSurfaceLow,
    surfaceContainer        = LightSurfaceContainer,
    surfaceContainerHigh    = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,

    surfaceBright           = Color(0xFFFFFFFF),
    surfaceDim              = Color(0xFFE9EEF5),

    outline              = LightOutline,
    outlineVariant       = LightOutlineVariant,

    error                = LightError,
    onError              = OnAccent,
    errorContainer       = LightErrorContainer,
    onErrorContainer     = OnLightErrorContainer,

    inverseSurface       = Color(0xFF1F2937),
    inverseOnSurface     = Color(0xFFF9FAFB),
    inversePrimary       = Color(0xFF93C5FD),

    scrim                = Color(0x66000000),
)

// ── Theme ───────────────────────────────────────────────────────────────────

@Composable
fun BggCombinedTheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.DARK  -> DarkColorScheme
        AppTheme.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}