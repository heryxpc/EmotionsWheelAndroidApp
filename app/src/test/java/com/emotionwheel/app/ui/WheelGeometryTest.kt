package com.emotionwheel.app.ui

import androidx.compose.ui.geometry.Offset
import com.emotionwheel.app.data.catalog.EmotionFamily
import com.emotionwheel.app.data.catalog.EmotionLevel
import com.emotionwheel.app.ui.wheel.WheelGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The wheel is hit-tested with polar arithmetic rather than path collision, so these
 * tests are what stands between a tap and the wrong word.
 */
class WheelGeometryTest {

    private val center = Offset(500f, 500f)
    private val radius = 500f

    /** A point at [angle] degrees clockwise from the top, [fraction] of the way out. */
    private fun point(angle: Float, fraction: Float): Offset {
        val radians = Math.toRadians(angle.toDouble())
        return Offset(
            x = center.x + radius * fraction * sin(radians).toFloat(),
            y = center.y - radius * fraction * cos(radians).toFloat(),
        )
    }

    @Test
    fun `twelve o clock is zero degrees and three o clock is ninety`() {
        assertEquals(0f, WheelGeometry.angleOf(Offset(500f, 100f), center), 0.01f)
        assertEquals(90f, WheelGeometry.angleOf(Offset(900f, 500f), center), 0.01f)
        assertEquals(180f, WheelGeometry.angleOf(Offset(500f, 900f), center), 0.01f)
        assertEquals(270f, WheelGeometry.angleOf(Offset(100f, 500f), center), 0.01f)
    }

    @Test
    fun `families sit clockwise from the top in the order of the printed wheel`() {
        val expected = listOf(
            30f to EmotionFamily.SURPRISE,
            90f to EmotionFamily.ANGER,
            150f to EmotionFamily.JOY,
            210f to EmotionFamily.FEAR,
            270f to EmotionFamily.SADNESS,
            330f to EmotionFamily.DISGUST,
        )
        expected.forEach { (angle, family) ->
            val sector = WheelGeometry.sectorAt(point(angle, 0.2f), center, radius, rotation = 0f)
            assertEquals("at $angle degrees", family, sector?.family)
            assertEquals(EmotionLevel.CORE, sector?.level)
        }
    }

    @Test
    fun `the three rings are told apart by distance from the center`() {
        assertEquals(EmotionLevel.CORE, ringAt(0.20f))
        assertEquals(EmotionLevel.MIDDLE, ringAt(0.50f))
        assertEquals(EmotionLevel.OUTER, ringAt(0.85f))
    }

    private fun ringAt(fraction: Float) =
        WheelGeometry.sectorAt(point(30f, fraction), center, radius, rotation = 0f)?.level

    @Test
    fun `a ring holds seven slots numbered clockwise from the leading edge`() {
        val family = EmotionFamily.ANGER // spans 60 to 120 degrees
        repeat(WheelGeometry.SECTORS_PER_RING) { index ->
            val midAngle = 60f + (index + 0.5f) * WheelGeometry.SECTOR_SWEEP
            val sector =
                WheelGeometry.sectorAt(point(midAngle, 0.5f), center, radius, rotation = 0f)
            assertEquals(family, sector?.family)
            assertEquals("slot at $midAngle degrees", index, sector?.index)
        }
    }

    @Test
    fun `spinning the wheel moves what a given spot selects`() {
        val spot = point(30f, 0.5f)
        assertEquals(
            EmotionFamily.SURPRISE,
            WheelGeometry.sectorAt(spot, center, radius, rotation = 0f)?.family,
        )
        // Turn the wheel a sixth of a turn clockwise and its neighbor comes around.
        assertEquals(
            EmotionFamily.DISGUST,
            WheelGeometry.sectorAt(spot, center, radius, rotation = 60f)?.family,
        )
    }

    /**
     * Mirrors what EmotionWheel does before hit-testing a touch: the finger lands in
     * view coordinates, the sectors live in the wheel's own, and the zoom sits between
     * the two. Getting this backwards picks a neighbouring word, which is exactly the
     * kind of error a user would blame on their aim.
     */
    private fun unzoom(position: Offset, scale: Float, pan: Offset): Offset =
        center + (position - center - pan) / scale

    @Test
    fun `undoing the zoom lands on the same sector the user aimed at`() {
        val aimed = point(312.9f, 0.505f) // saturación, in the middle ring of ASCO
        val expected = WheelGeometry.sectorAt(aimed, center, radius, rotation = 0f)
        assertEquals(EmotionFamily.DISGUST, expected?.family)
        assertEquals(1, expected?.index)

        // Zoomed 2.5x the same word is drawn somewhere else on screen; touching it
        // there has to resolve to the same sector.
        val scale = 2.5f
        val pan = Offset(-120f, 60f)
        val onScreen = center + (aimed - center) * scale + pan

        val resolved = WheelGeometry.sectorAt(
            unzoom(onScreen, scale, pan), center, radius, rotation = 0f,
        )
        assertEquals(expected, resolved)
    }

    @Test
    fun `at rest the inverse transform changes nothing`() {
        val spot = point(150f, 0.8f)
        assertEquals(spot, unzoom(spot, scale = 1f, pan = Offset.Zero))
    }

    @Test
    fun `zoom and rotation compose without interfering`() {
        val spot = point(30f, 0.5f)
        val scale = 3f
        val pan = Offset(200f, -40f)
        val onScreen = center + (spot - center) * scale + pan

        // Same touch, wheel turned a sixth of a turn: the neighbouring family answers,
        // exactly as it does at 1x.
        assertEquals(
            EmotionFamily.SURPRISE,
            WheelGeometry.sectorAt(unzoom(onScreen, scale, pan), center, radius, 0f)?.family,
        )
        assertEquals(
            EmotionFamily.DISGUST,
            WheelGeometry.sectorAt(unzoom(onScreen, scale, pan), center, radius, 60f)?.family,
        )
    }

    @Test
    fun `touches outside the wheel and in the gaps between rings select nothing`() {
        assertNull(WheelGeometry.sectorAt(point(30f, 1.4f), center, radius, rotation = 0f))
        assertNull(WheelGeometry.sectorAt(point(30f, 0.342f), center, radius, rotation = 0f))
        assertNull(WheelGeometry.sectorAt(point(30f, 0.667f), center, radius, rotation = 0f))
    }
}
