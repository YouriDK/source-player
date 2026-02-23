package com.source.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Plus Jakarta Sans is shipped via bundled font file or Google Fonts
// Here we use system fallback — replace with actual font resource when assets added
val PlusJakartaSans = FontFamily.Default // swap: FontFamily(Font(R.font.plus_jakarta_sans))

val SourceTypography =
        Typography(
                displayLarge =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                lineHeight = 40.sp
                        ),
                displayMedium =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                lineHeight = 36.sp
                        ),
                headlineLarge =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 22.sp,
                                lineHeight = 28.sp
                        ),
                headlineMedium =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                lineHeight = 24.sp
                        ),
                titleLarge =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                        ),
                titleMedium =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                bodyLarge =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp
                        ),
                bodyMedium =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                bodySmall =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                        ),
                labelLarge =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                        ),
                labelSmall =
                        TextStyle(
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                        ),
        )
