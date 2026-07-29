package com.chupacabra.evchargeestimation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OnDarkPrimary,
    primaryContainer = Color(0xFF003D4A),
    onPrimaryContainer = NeonCyan,
    secondary = NeonMint,
    onSecondary = OnDarkPrimary,
    secondaryContainer = Color(0xFF003D2A),
    onSecondaryContainer = NeonMint,
    tertiary = NeonViolet,
    onTertiary = SoftWhite,
    tertiaryContainer = Color(0xFF2A2450),
    onTertiaryContainer = Color(0xFFD4CFFF),
    background = DeepSpace,
    onBackground = SoftWhite,
    surface = VoidNavy,
    onSurface = SoftWhite,
    surfaceVariant = PanelSlate,
    onSurfaceVariant = MutedSteel,
    surfaceContainerHighest = ElevatedPanel,
    surfaceContainerHigh = PanelSlate,
    surfaceContainer = VoidNavy,
    surfaceContainerLow = DeepSpace,
    surfaceContainerLowest = Color(0xFF03050A),
    outline = Color(0xFF3A4A68),
    outlineVariant = Color(0xFF243048),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF3B0008),
    errorContainer = Color(0xFF5C1520),
    onErrorContainer = Color(0xFFFFB4BC),
    inverseSurface = SoftWhite,
    inverseOnSurface = DeepSpace,
    inversePrimary = ElectricTeal,
    scrim = Color(0xCC000000)
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricTeal,
    onPrimary = OnLightPrimary,
    primaryContainer = Color(0xFFC8F5FF),
    onPrimaryContainer = Color(0xFF003640),
    secondary = ElectricGreen,
    onSecondary = OnLightPrimary,
    secondaryContainer = Color(0xFFC8FFE8),
    onSecondaryContainer = Color(0xFF003822),
    tertiary = NeonViolet,
    onTertiary = OnLightPrimary,
    tertiaryContainer = Color(0xFFE8E4FF),
    onTertiaryContainer = Color(0xFF2A1F70),
    background = LightSurface,
    onBackground = DarkInk,
    surface = LightPanel,
    onSurface = DarkInk,
    surfaceVariant = Color(0xFFE4EBF5),
    onSurfaceVariant = SoftInk,
    surfaceContainerHighest = Color(0xFFDCE6F2),
    surfaceContainerHigh = Color(0xFFE8EEF7),
    surfaceContainer = Color(0xFFEEF3FA),
    surfaceContainerLow = LightSurface,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    outline = LightOutline,
    outlineVariant = Color(0xFFD0DAE8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = DarkInk,
    inverseOnSurface = SoftWhite,
    inversePrimary = NeonCyan,
    scrim = Color(0x99000000)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun EVChargeEstimationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Fixed brand palette (not wallpaper dynamic) so the EV HUD look stays consistent
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
