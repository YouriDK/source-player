package com.source.player.ui.theme

import androidx.compose.ui.graphics.Color

// ── Vinyl palette (dark, default) ──────────────────────────────────────────
// Warm graphite, almost-black with amber undertone. All tokens computed once
// from the design-handoff OKLCH specs.
val VinylBg       = oklchToColor(0.145f, 0.008f,  60f)
val VinylSurface  = oklchToColor(0.185f, 0.010f,  60f)
val VinylSurface2 = oklchToColor(0.225f, 0.012f,  60f)
val VinylHair     = oklchToColor(0.280f, 0.012f,  60f)
val VinylText     = oklchToColor(0.965f, 0.005f,  80f)
val VinylTextDim  = oklchToColor(0.780f, 0.010f,  70f)
val VinylTextMute = oklchToColor(0.580f, 0.012f,  70f)

// ── Vinyl palette (light) — warm paper, ink ────────────────────────────────
val VinylBgLight       = oklchToColor(0.970f, 0.008f, 80f)
val VinylSurfaceLight  = oklchToColor(0.945f, 0.010f, 75f)
val VinylSurface2Light = oklchToColor(0.920f, 0.012f, 75f)
val VinylHairLight     = oklchToColor(0.870f, 0.012f, 75f)
val VinylTextLight     = oklchToColor(0.200f, 0.012f, 60f)
val VinylTextDimLight  = oklchToColor(0.420f, 0.012f, 65f)
val VinylTextMuteLight = oklchToColor(0.580f, 0.012f, 70f)

// ── Utility ────────────────────────────────────────────────────────────────
val ErrorRed    = Color(0xFFFF4D4D)
val SuccessGreen = Color(0xFF2ECC71)

// ── Legacy aliases (kept so unmigrated screens keep compiling) ─────────────
// Each legacy token resolves to its Vinyl equivalent. Gradually removed as
// screens are rewritten in later phases.
val SourceBlue      = accentForHue(60f)            // default Amber hue
val SourceBlueDim   = accentForHue(60f, dark = false)
val SourceBlueLight = accentForHue(60f)
val Surface       = VinylBg
val SurfaceCard   = VinylSurface
val SurfaceSheet  = VinylSurface2
val SurfaceNavBar = VinylBg
val OnSurface     = VinylText
val OnSurfaceMid  = VinylTextDim
val OnSurfaceLow  = VinylTextMute
val Divider       = VinylHair
