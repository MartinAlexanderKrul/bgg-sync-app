package com.bgg.combined.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Theme choice ───────────────────────────────────────────────────────────
enum class AppTheme(val label: String) {
    DARK("Dark (Amber)"),
    LIGHT("Light")
}

// ── Dark palette ───────────────────────────────────────────────────────────
private val AmberGold        = Color(0xFFFEB316)
private val AmberDark        = Color(0xFFC98C00)
private val AmberContainer   = Color(0xFF3A2C00)
private val AmberLight       = Color(0xFFFFDFA0)
private val AmberDeepDark    = Color(0xFF2A2200)

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
    // Explicit surface containers so NavigationBar / sheets use our palette
    surfaceContainerLowest  = Background,
    surfaceContainerLow     = Background,
    surfaceContainer        = Surface,        // NavigationBar background
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

// ── Light palette — Material3 defaults (blue/purple) ──────────────────────
private val LightColorScheme = lightColorScheme()

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
