package com.source.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// ── Extended color slot — Vinyl tokens + brand accents ─────────────────────
@Immutable
data class SourceColors(
        // Vinyl tokens
        val bg: Color,
        val surface: Color,
        val surface2: Color,
        val hair: Color,
        val text: Color,
        val textDim: Color,
        val textMute: Color,
        // Accent
        val accent: Color,
        val accentDim: Color,
        // Utility
        val error: Color,
        val success: Color,
        // Legacy aliases (populated with Vinyl equivalents so unmigrated
        // screens keep working while the redesign rolls out)
        val surfaceCard: Color,
        val surfaceSheet: Color,
        val onSurfaceMid: Color,
        val onSurfaceLow: Color,
        val divider: Color,
)

private fun vinylDarkColors(accent: Color): SourceColors =
        SourceColors(
                bg = VinylBg,
                surface = VinylSurface,
                surface2 = VinylSurface2,
                hair = VinylHair,
                text = VinylText,
                textDim = VinylTextDim,
                textMute = VinylTextMute,
                accent = accent,
                accentDim = accent.copy(alpha = 0.25f),
                error = ErrorRed,
                success = SuccessGreen,
                surfaceCard = VinylSurface,
                surfaceSheet = VinylSurface2,
                onSurfaceMid = VinylTextDim,
                onSurfaceLow = VinylTextMute,
                divider = VinylHair,
        )

private fun vinylLightColors(accent: Color): SourceColors =
        SourceColors(
                bg = VinylBgLight,
                surface = VinylSurfaceLight,
                surface2 = VinylSurface2Light,
                hair = VinylHairLight,
                text = VinylTextLight,
                textDim = VinylTextDimLight,
                textMute = VinylTextMuteLight,
                accent = accent,
                accentDim = accent.copy(alpha = 0.25f),
                error = ErrorRed,
                success = SuccessGreen,
                surfaceCard = VinylSurfaceLight,
                surfaceSheet = VinylSurface2Light,
                onSurfaceMid = VinylTextDimLight,
                onSurfaceLow = VinylTextMuteLight,
                divider = VinylHairLight,
        )

val LocalSourceColors = staticCompositionLocalOf { vinylDarkColors(accentForHue(60f)) }

// ── Material3 ColorScheme — Vinyl-mapped ──────────────────────────────────
private fun darkScheme(accent: Color) =
        darkColorScheme(
                primary = accent,
                onPrimary = VinylBg,
                primaryContainer = accent.copy(alpha = 0.15f),
                secondary = accent,
                background = VinylBg,
                surface = VinylBg,
                surfaceVariant = VinylSurface,
                onBackground = VinylText,
                onSurface = VinylText,
                onSurfaceVariant = VinylTextDim,
                outline = VinylHair,
                error = ErrorRed,
        )

private fun lightScheme(accent: Color) =
        lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.15f),
                secondary = accent,
                background = VinylBgLight,
                surface = VinylBgLight,
                surfaceVariant = VinylSurfaceLight,
                onBackground = VinylTextLight,
                onSurface = VinylTextLight,
                onSurfaceVariant = VinylTextDimLight,
                outline = VinylHairLight,
                error = ErrorRed,
        )

// ── SourceTheme — entry point ─────────────────────────────────────────────
@Composable
fun SourceTheme(
        darkTheme: Boolean = true,
        accentHue: Float = 60f,
        // Kept for binary-compat with the old picker; Vinyl fixes the family.
        fontFamily: FontFamily = Geist,
        content: @Composable () -> Unit,
) {
        val accent = remember(darkTheme, accentHue) { accentForHue(accentHue, darkTheme) }
        val sourceColors =
                remember(darkTheme, accent) {
                        if (darkTheme) vinylDarkColors(accent) else vinylLightColors(accent)
                }
        val colorScheme =
                remember(darkTheme, accent) {
                        if (darkTheme) darkScheme(accent) else lightScheme(accent)
                }
        val textStyles = remember { SourceTextStyles.build() }
        val typography = remember(fontFamily) { buildTypography(fontFamily) }

        CompositionLocalProvider(
                LocalSourceColors provides sourceColors,
                LocalSourceText provides textStyles,
        ) {
                MaterialTheme(
                        colorScheme = colorScheme,
                        typography = typography,
                        shapes = SourceShapes,
                        content = content,
                )
        }
}

// Convenience accessors — avoid passing SourceColors / SourceTextStyles via
// parameters.
val MaterialTheme.sourceColors: SourceColors
        @Composable get() = LocalSourceColors.current

val MaterialTheme.sourceText: SourceTextStyles
        @Composable get() = LocalSourceText.current
