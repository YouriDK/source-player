@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.source.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.source.player.R

// ─── Font families (bundled in res/font/) ─────────────────────────────────

/** Editorial display serif — track titles, section headlines, album names. */
val InstrumentSerif =
        FontFamily(
                Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
                Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
        )

/** Body / UI sans — labels, buttons, nav, metadata. */
val Geist =
        FontFamily(
                Font(
                        R.font.geist_variable,
                        FontWeight.Normal,
                        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
                ),
                Font(
                        R.font.geist_variable,
                        FontWeight.Medium,
                        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
                ),
                Font(
                        R.font.geist_variable,
                        FontWeight.SemiBold,
                        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
                ),
                Font(
                        R.font.geist_variable,
                        FontWeight.Bold,
                        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
                ),
        )

/** Tabular numerics, kickers, editorial markers. */
val GeistMono =
        FontFamily(
                Font(
                        R.font.geist_mono_variable,
                        FontWeight.Normal,
                        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
                ),
                Font(
                        R.font.geist_mono_variable,
                        FontWeight.Medium,
                        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
                ),
        )

// ─── Vinyl named type scale ────────────────────────────────────────────────

@Immutable
data class SourceTextStyles(
        // Display — serif italic, the editorial voice
        val heroTitle140: TextStyle,
        val homeFeature96: TextStyle,
        val findHeadline72: TextStyle,
        val libraryLetter64: TextStyle,
        val queueHeadline56: TextStyle,
        val editorialHeadline32: TextStyle,
        val masthead30: TextStyle,
        val categoryLabel26: TextStyle,
        val categoryLabel24: TextStyle,
        val trackTitle22: TextStyle,
        val libraryRow19: TextStyle,
        val rotationTitle18: TextStyle,
        // Sans — workhorse
        val bodyLarge15: TextStyle,
        val bodyMedium14: TextStyle,
        val editorialBody13: TextStyle,
        val pillLabel12: TextStyle,
        val metaSmall115: TextStyle,
        // Mono — numerics & kickers
        val durationMono105: TextStyle,
        val kickerMono10: TextStyle,
        val kickerMono95: TextStyle,
) {
        companion object {
                fun build(): SourceTextStyles =
                        SourceTextStyles(
                                heroTitle140 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 140.sp,
                                                lineHeight = 0.82.em,
                                                letterSpacing = (-0.036).em, // ≈ -5/140
                                        ),
                                homeFeature96 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 96.sp,
                                                lineHeight = 0.85.em,
                                                letterSpacing = (-0.036).em,
                                        ),
                                findHeadline72 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 72.sp,
                                                lineHeight = 0.90.em,
                                                letterSpacing = (-0.028).em,
                                        ),
                                libraryLetter64 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 64.sp,
                                                lineHeight = 1.0.em,
                                                letterSpacing = (-0.025).em,
                                        ),
                                queueHeadline56 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 56.sp,
                                                lineHeight = 0.90.em,
                                                letterSpacing = (-0.025).em,
                                        ),
                                editorialHeadline32 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 32.sp,
                                                lineHeight = 1.05.em,
                                                letterSpacing = (-0.019).em,
                                        ),
                                masthead30 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 30.sp,
                                                lineHeight = 1.0.em,
                                                letterSpacing = (-0.027).em,
                                        ),
                                categoryLabel26 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 26.sp,
                                                letterSpacing = (-0.015).em,
                                        ),
                                categoryLabel24 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 24.sp,
                                                lineHeight = 1.05.em,
                                                letterSpacing = (-0.012).em,
                                        ),
                                trackTitle22 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 22.sp,
                                                lineHeight = 1.1.em,
                                                letterSpacing = (-0.014).em,
                                        ),
                                libraryRow19 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontSize = 19.sp,
                                                lineHeight = 1.15.em,
                                                letterSpacing = (-0.010).em,
                                        ),
                                rotationTitle18 =
                                        TextStyle(
                                                fontFamily = InstrumentSerif,
                                                fontStyle = FontStyle.Italic,
                                                fontSize = 18.sp,
                                                lineHeight = 1.15.em,
                                                letterSpacing = (-0.011).em,
                                        ),
                                bodyLarge15 =
                                        TextStyle(
                                                fontFamily = Geist,
                                                fontSize = 15.sp,
                                                lineHeight = 1.4.em,
                                        ),
                                bodyMedium14 =
                                        TextStyle(
                                                fontFamily = Geist,
                                                fontSize = 14.sp,
                                                lineHeight = 1.4.em,
                                        ),
                                editorialBody13 =
                                        TextStyle(
                                                fontFamily = Geist,
                                                fontSize = 13.sp,
                                                lineHeight = 1.55.em,
                                        ),
                                pillLabel12 =
                                        TextStyle(
                                                fontFamily = Geist,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                letterSpacing = 0.008.em,
                                        ),
                                metaSmall115 =
                                        TextStyle(
                                                fontFamily = Geist,
                                                fontSize = 11.5.sp,
                                                lineHeight = 1.3.em,
                                        ),
                                durationMono105 =
                                        TextStyle(
                                                fontFamily = GeistMono,
                                                fontSize = 10.5.sp,
                                        ),
                                kickerMono10 =
                                        TextStyle(
                                                fontFamily = GeistMono,
                                                fontSize = 10.sp,
                                                letterSpacing = 0.14.em,
                                        ),
                                kickerMono95 =
                                        TextStyle(
                                                fontFamily = GeistMono,
                                                fontSize = 9.5.sp,
                                                letterSpacing = 0.16.em,
                                        ),
                        )
        }
}

val LocalSourceText = compositionLocalOf { SourceTextStyles.build() }

// ─── Legacy AppFont enum (kept so Settings picker keeps compiling) ────────
//
// The Vinyl direction fixes the type families — the font-family picker is
// effectively retired. We keep the enum so the Settings screen doesn't break
// while we rewrite it; every entry now resolves to [Geist].
enum class AppFont(val label: String) {
        PlusJakartaSans("Plus Jakarta Sans"),
        Inter("Inter"),
        Roboto("Roboto"),
        Montserrat("Montserrat"),
        Poppins("Poppins"),
}

fun AppFont.toFontFamily(): FontFamily = Geist

// ─── Material3 Typography ─────────────────────────────────────────────────
// Mapped so legacy screens (Folders, old Settings, Tag Editor) keep rendering
// with the Vinyl families. Display slots use Instrument Serif; everything
// else uses Geist.
fun buildTypography(fontFamily: FontFamily = Geist) =
        Typography(
                displayLarge =
                        TextStyle(
                                fontFamily = InstrumentSerif,
                                fontSize = 32.sp,
                                lineHeight = 40.sp,
                                letterSpacing = (-0.6).sp,
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = InstrumentSerif,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                        ),
                headlineLarge =
                        TextStyle(
                                fontFamily = InstrumentSerif,
                                fontSize = 22.sp,
                                lineHeight = 28.sp,
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = InstrumentSerif,
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                        ),
                titleLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                        ),
                bodyLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                        ),
                labelLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = GeistMono,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                letterSpacing = 0.1.sp,
                        ),
        )

// Back-compat reference so any import site still compiles.
val PlusJakartaSans: FontFamily = Geist
val SourceTypography = buildTypography(Geist)
