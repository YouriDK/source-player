package com.source.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.source.player.ui.theme.sourceColors
import com.source.player.ui.theme.sourceText

/**
 * Mono 10 all-caps editorial kicker (e.g. "SIDE A · TRACK 04", "NOW PLAYING").
 * Optional trailing counter ("02 / 07") and hairline filler recreate the
 * editorial-divider row from the Vinyl design.
 */
@Composable
fun SectionKicker(
        label: String,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.sourceColors.textMute,
        align: TextAlign = TextAlign.Unspecified,
) {
    Text(
            text = label.uppercase(),
            style = MaterialTheme.sourceText.kickerMono10.copy(textAlign = align),
            color = color,
            modifier = modifier,
    )
}

/**
 * Section header row: "KICKER ─── optional counter". Matches the 34/24/14
 * editorial-divider pattern (top padding decided by caller).
 */
@Composable
fun EditorialDivider(
        label: String,
        modifier: Modifier = Modifier,
        counter: String? = null,
        labelColor: Color = MaterialTheme.sourceColors.textMute,
) {
    Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionKicker(label, color = labelColor)
        Hairline(modifier = Modifier.weight(1f))
        if (counter != null) SectionKicker(counter, color = MaterialTheme.sourceColors.textMute)
    }
}

/** 1 px horizontal rule in the Vinyl `hair` tone. */
@Composable
fun Hairline(
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.sourceColors.hair,
        thickness: Dp = 1.dp,
) {
    Box(modifier = modifier.fillMaxWidth().height(thickness).background(color))
}

/** Vertical hair rule (used for the Library A-Z rail border). */
@Composable
fun VerticalHairline(
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.sourceColors.hair,
        thickness: Dp = 1.dp,
) {
    Box(modifier = modifier.fillMaxHeight().width(thickness).background(color))
}

/**
 * Pill-shaped tab chip used in Library and search-like rows. Active state
 * inverts: text-color background + background-color text.
 */
@Composable
fun PillTab(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.sourceColors
    val bg = if (selected) colors.text else Color.Transparent
    val fg = if (selected) colors.bg else colors.textDim
    Box(
            modifier =
                    modifier.clip(CircleShape)
                            .background(bg)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.sourceText.pillLabel12, color = fg)
    }
}

/**
 * Geist Mono tabular numeric text — used for durations, timestamps, and track
 * numbers. Tabular-nums feature is enabled by the font itself; the helper
 * exists purely to keep call sites tiny and consistent.
 */
@Composable
fun MonoText(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.sourceColors.textMute,
        size: MonoSize = MonoSize.S10,
) {
    val style =
            when (size) {
                MonoSize.S10 -> MaterialTheme.sourceText.kickerMono10
                MonoSize.S95 -> MaterialTheme.sourceText.kickerMono95
                MonoSize.S105 -> MaterialTheme.sourceText.durationMono105
            }
    Text(text = text, modifier = modifier, style = style, color = color)
}

enum class MonoSize { S95, S10, S105 }
