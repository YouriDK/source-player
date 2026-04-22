package com.source.player.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * OKLCH → sRGB conversion. Based on Björn Ottosson's reference implementation.
 *
 * - `l` in `[0, 1]` (lightness)
 * - `c` is chroma, typically `[0, 0.4]`
 * - `hueDegrees` in `[0, 360)`
 *
 * The result is clamped into the sRGB gamut; out-of-gamut colors are returned
 * as their nearest representable sRGB value, not as errors.
 */
fun oklchToColor(l: Float, c: Float, hueDegrees: Float): Color {
    val hRad = Math.toRadians(hueDegrees.toDouble())
    val a = c * cos(hRad).toFloat()
    val b = c * sin(hRad).toFloat()
    return oklabToColor(l, a, b)
}

private fun oklabToColor(l: Float, a: Float, b: Float): Color {
    // OKLab → linear LMS
    val lPrime = l + 0.3963377774f * a + 0.2158037573f * b
    val mPrime = l - 0.1055613458f * a - 0.0638541728f * b
    val sPrime = l - 0.0894841775f * a - 1.2914855480f * b

    val lLin = lPrime * lPrime * lPrime
    val mLin = mPrime * mPrime * mPrime
    val sLin = sPrime * sPrime * sPrime

    // Linear LMS → linear sRGB
    val rLin = +4.0767416621f * lLin - 3.3077115913f * mLin + 0.2309699292f * sLin
    val gLin = -1.2684380046f * lLin + 2.6097574011f * mLin - 0.3413193965f * sLin
    val bLin = -0.0041960863f * lLin - 0.7034186147f * mLin + 1.7076147010f * sLin

    return Color(
            red = srgbGamma(rLin.coerceIn(0f, 1f)),
            green = srgbGamma(gLin.coerceIn(0f, 1f)),
            blue = srgbGamma(bLin.coerceIn(0f, 1f)),
    )
}

private fun srgbGamma(linear: Float): Float =
        if (linear <= 0.0031308f) 12.92f * linear
        else 1.055f * linear.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f

/** Standard Vinyl accent: `oklch(0.74 0.14 H)` for dark; `oklch(0.58 0.15 H)` for light. */
fun accentForHue(hueDegrees: Float, dark: Boolean = true): Color =
        if (dark) oklchToColor(0.74f, 0.14f, hueDegrees)
        else oklchToColor(0.58f, 0.15f, hueDegrees)
