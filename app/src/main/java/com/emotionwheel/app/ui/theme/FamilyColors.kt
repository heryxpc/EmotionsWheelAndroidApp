package com.emotionwheel.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.emotionwheel.app.data.catalog.EmotionFamily
import com.emotionwheel.app.data.catalog.EmotionLevel

/**
 * The three shades a family paints on the wheel, sampled from the printed original:
 * the core wedge is the deepest, the middle ring sits at the base tone and the outer
 * ring is lightened so the two rings stay distinguishable at a glance.
 */
@Immutable
data class FamilyPalette(
    val core: Color,
    val middle: Color,
    val outer: Color,
) {
    fun forLevel(level: Int): Color = when (level) {
        EmotionLevel.CORE -> core
        EmotionLevel.MIDDLE -> middle
        else -> outer
    }
}

private val Palettes: Map<EmotionFamily, FamilyPalette> = mapOf(
    EmotionFamily.SURPRISE to FamilyPalette(Color(0xFFD4671F), Color(0xFFE07B39), Color(0xFFEC9A5C)),
    EmotionFamily.ANGER to FamilyPalette(Color(0xFFC03A34), Color(0xFFD0473F), Color(0xFFDE6F62)),
    EmotionFamily.JOY to FamilyPalette(Color(0xFFD9AE2F), Color(0xFFE8C34A), Color(0xFFEFD375)),
    EmotionFamily.FEAR to FamilyPalette(Color(0xFF6A5794), Color(0xFF7E6CA8), Color(0xFF9A8BBE)),
    EmotionFamily.SADNESS to FamilyPalette(Color(0xFF4874A8), Color(0xFF5B8AC0), Color(0xFF83A9D2)),
    EmotionFamily.DISGUST to FamilyPalette(Color(0xFF3D8A55), Color(0xFF4FA268), Color(0xFF74B889)),
)

/** Neutral tone for emotions typed by hand that the wheel does not name. */
val UnmappedEmotionColor = Color(0xFF8A8A8E)

val EmotionFamily.palette: FamilyPalette get() = Palettes.getValue(this)

/** Base color of a family, the one used for chips and list accents. */
val EmotionFamily.color: Color get() = palette.middle

/**
 * Black or white, whichever stays readable on [background]. The yellow of JOY is far
 * too light for white text while the purple of FEAR is far too dark for black.
 */
fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White

/** Family color for an entry, or the neutral tone when it has no wheel emotion. */
@Composable
fun accentColorFor(families: List<EmotionFamily>): Color = when {
    families.isEmpty() -> UnmappedEmotionColor
    else -> families.first().color
}

@Composable
fun subtleSurface(): Color = MaterialTheme.colorScheme.surfaceVariant
