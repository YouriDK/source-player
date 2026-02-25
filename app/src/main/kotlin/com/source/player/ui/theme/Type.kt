package com.source.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Available font families ────────────────────────────────────────────────
// Currently backed by system fallbacks; swap Font(R.font.xxx) when assets land.
enum class AppFont(val label: String) {
        PlusJakartaSans("Plus Jakarta Sans"),
        Inter("Inter"),
        Roboto("Roboto"),
        Montserrat("Montserrat"),
        Poppins("Poppins"),
}

fun AppFont.toFontFamily(): FontFamily =
        when (this) {
                AppFont.PlusJakartaSans -> FontFamily.Default
                AppFont.Inter -> FontFamily.SansSerif
                AppFont.Roboto -> FontFamily.SansSerif
                AppFont.Montserrat -> FontFamily.SansSerif
                AppFont.Poppins -> FontFamily.SansSerif
        }

// Legacy reference – kept so nothing breaks while we wire up the picker
val PlusJakartaSans = FontFamily.Default

val SourceTypography = buildTypography(FontFamily.Default)

fun buildTypography(fontFamily: FontFamily) =
        Typography(
                displayLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                lineHeight = 40.sp
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                lineHeight = 36.sp
                        ),
                headlineLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                lineHeight = 24.sp
                        ),
                titleLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                bodyLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                        ),
                labelLarge =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                        ),
                labelMedium =
                        TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                        ),
        )
