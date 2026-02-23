package com.source.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// -------------------------------------------------------------------------
// Extended color slot — lets every screen reach extra brand tokens
// -------------------------------------------------------------------------
@Immutable
data class SourceColors(
        val accent: Color,
        val accentDim: Color,
        val surface: Color,
        val surfaceCard: Color,
        val surfaceSheet: Color,
        val onSurfaceMid: Color,
        val onSurfaceLow: Color,
        val divider: Color,
        val error: Color,
        val success: Color,
)

val LocalSourceColors = staticCompositionLocalOf {
  SourceColors(
          accent = SourceBlue,
          accentDim = SourceBlueDim,
          surface = Surface,
          surfaceCard = SurfaceCard,
          surfaceSheet = SurfaceSheet,
          onSurfaceMid = OnSurfaceMid,
          onSurfaceLow = OnSurfaceLow,
          divider = Divider,
          error = ErrorRed,
          success = SuccessGreen,
  )
}

// -------------------------------------------------------------------------
// Dark color scheme (Material3)
// -------------------------------------------------------------------------
private fun darkScheme(accent: Color) =
        darkColorScheme(
                primary = accent,
                onPrimary = Color.White,
                primaryContainer = accent.copy(alpha = 0.15f),
                secondary = accent,
                background = Surface,
                surface = Surface,
                surfaceVariant = SurfaceCard,
                onBackground = OnSurface,
                onSurface = OnSurface,
                onSurfaceVariant = OnSurfaceMid,
                outline = Divider,
                error = ErrorRed,
        )

private fun lightScheme(accent: Color) =
        lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                background = Color(0xFFF5F5FA),
                surface = Color(0xFFFFFFFF),
                onBackground = Color(0xFF0A0A0F),
                onSurface = Color(0xFF0A0A0F),
                error = ErrorRed,
        )

// -------------------------------------------------------------------------
// SourceTheme — entry point for the entire app
// -------------------------------------------------------------------------
@Composable
fun SourceTheme(
        darkTheme: Boolean = true,
        accentColor: Color = SourceBlue,
        content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) darkScheme(accentColor) else lightScheme(accentColor)
  val sourceColors = LocalSourceColors.current.copy(accent = accentColor)

  CompositionLocalProvider(LocalSourceColors provides sourceColors) {
    MaterialTheme(
            colorScheme = colorScheme,
            typography = SourceTypography,
            shapes = SourceShapes,
            content = content,
    )
  }
}

// Convenience accessor — avoid passing down SourceColors via parameters
val MaterialTheme.sourceColors: SourceColors
  @Composable get() = LocalSourceColors.current
