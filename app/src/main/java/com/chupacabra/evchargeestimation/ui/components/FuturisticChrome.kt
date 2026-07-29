package com.chupacabra.evchargeestimation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chupacabra.evchargeestimation.ui.theme.DeepSpace
import com.chupacabra.evchargeestimation.ui.theme.ElevatedPanel
import com.chupacabra.evchargeestimation.ui.theme.NeonCyan
import com.chupacabra.evchargeestimation.ui.theme.NeonMint
import com.chupacabra.evchargeestimation.ui.theme.NeonViolet
import com.chupacabra.evchargeestimation.ui.theme.PanelSlate
import com.chupacabra.evchargeestimation.ui.theme.VoidNavy

/**
 * Subtle radial-ish mesh gradient behind content for a sci-fi HUD feel.
 */
@Composable
fun FuturisticBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val dark = isSystemInDarkTheme()
    val brush = if (dark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A1228),
                DeepSpace,
                Color(0xFF060A14)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE8F6FA),
                MaterialTheme.colorScheme.background,
                Color(0xFFF0F4FA)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush)
    ) {
        // Soft glow orbs (decorative)
        if (dark) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonCyan.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomEnd)
                    .fillMaxWidth(0.7f)
                    .height(280.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NeonViolet.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        content()
    }
}

/**
 * Glass-style panel: translucent surface + thin luminous border.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentBorder: Boolean = false,
    cornerRadius: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val borderColor = when {
        accentBorder && dark -> NeonCyan.copy(alpha = 0.45f)
        accentBorder -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        dark -> Color.White.copy(alpha = 0.08f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val container = if (dark) {
        ElevatedPanel.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape),
        shape = shape,
        color = container,
        tonalElevation = if (dark) 0.dp else 1.dp,
        shadowElevation = if (dark) 0.dp else 2.dp
    ) {
        content()
    }
}

/**
 * Thin accent underline used under section titles.
 */
@Composable
fun NeonAccentBar(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .fillMaxWidth(0.22f)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
                Brush.horizontalGradient(
                    if (dark) listOf(NeonCyan, NeonMint) else listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
    )
}

/**
 * Subtle top edge glow for navigation / bottom chrome.
 */
@Composable
fun glowBorderBrush(dark: Boolean = isSystemInDarkTheme()): Brush {
    return if (dark) {
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                NeonCyan.copy(alpha = 0.55f),
                NeonMint.copy(alpha = 0.35f),
                Color.Transparent
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                Color.Transparent
            )
        )
    }
}

@Composable
fun panelFill(dark: Boolean = isSystemInDarkTheme()): Color {
    return if (dark) VoidNavy.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface
}

@Composable
fun secondaryPanelFill(dark: Boolean = isSystemInDarkTheme()): Color {
    return if (dark) PanelSlate else MaterialTheme.colorScheme.surfaceVariant
}
