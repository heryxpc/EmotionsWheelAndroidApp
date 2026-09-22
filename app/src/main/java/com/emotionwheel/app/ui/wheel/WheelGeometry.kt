package com.emotionwheel.app.ui.wheel

import androidx.compose.ui.geometry.Offset
import com.emotionwheel.app.data.catalog.EmotionFamily
import com.emotionwheel.app.data.catalog.EmotionLevel
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Where every sector of the wheel sits, shared by the renderer and the hit tester so
 * the two can never drift apart.
 *
 * Angles are degrees measured clockwise from twelve o'clock, matching how the printed
 * wheel is read. Compose's own arcs start at three o'clock, hence [toSweepStart].
 */
object WheelGeometry {

    const val FAMILY_COUNT = 6
    const val SECTORS_PER_RING = 7

    const val FAMILY_SWEEP = 360f / FAMILY_COUNT          // 60 degrees
    const val SECTOR_SWEEP = FAMILY_SWEEP / SECTORS_PER_RING // ~8.57 degrees

    // Ring boundaries as a fraction of the wheel radius. The small gaps between rings
    // are what separates them visually without drawing an extra stroke.
    const val CORE_OUTER = 0.335f
    const val MIDDLE_INNER = 0.350f
    const val MIDDLE_OUTER = 0.660f
    const val OUTER_INNER = 0.675f
    const val OUTER_OUTER = 0.995f

    val families: List<EmotionFamily> = EmotionFamily.entries

    /** A sector of the wheel: which family, which ring, and which slot in that ring. */
    data class Sector(val family: EmotionFamily, val level: Int, val index: Int)

    fun innerRadiusFraction(level: Int): Float = when (level) {
        EmotionLevel.CORE -> 0f
        EmotionLevel.MIDDLE -> MIDDLE_INNER
        else -> OUTER_INNER
    }

    fun outerRadiusFraction(level: Int): Float = when (level) {
        EmotionLevel.CORE -> CORE_OUTER
        EmotionLevel.MIDDLE -> MIDDLE_OUTER
        else -> OUTER_OUTER
    }

    /** Clockwise-from-top angle where a sector begins. */
    fun startAngle(family: EmotionFamily, level: Int, index: Int): Float {
        val familyStart = families.indexOf(family) * FAMILY_SWEEP
        return if (level == EmotionLevel.CORE) familyStart else familyStart + index * SECTOR_SWEEP
    }

    fun sweep(level: Int): Float =
        if (level == EmotionLevel.CORE) FAMILY_SWEEP else SECTOR_SWEEP

    fun midAngle(family: EmotionFamily, level: Int, index: Int): Float =
        startAngle(family, level, index) + sweep(level) / 2f

    /** Converts a clockwise-from-top angle into the start angle Compose arcs expect. */
    fun toSweepStart(angleFromTop: Float): Float = angleFromTop - 90f

    fun normalize(angle: Float): Float = ((angle % 360f) + 360f) % 360f

    /**
     * Angle of [point] relative to [center], clockwise from twelve o'clock.
     * Screen coordinates grow downward, so the vertical term is negated.
     */
    fun angleOf(point: Offset, center: Offset): Float {
        val degrees = Math.toDegrees(
            atan2((point.x - center.x).toDouble(), (center.y - point.y).toDouble())
        ).toFloat()
        return normalize(degrees)
    }

    /**
     * Which sector a touch landed on, or null when it fell outside the wheel or into
     * one of the gaps between rings.
     *
     * [rotation] is how far the user has spun the wheel, and is undone here so the
     * hit test works against the wheel's own coordinates.
     */
    fun sectorAt(point: Offset, center: Offset, radius: Float, rotation: Float): Sector? {
        val distance = hypot(point.x - center.x, point.y - center.y)
        val fraction = distance / radius

        val level = when {
            fraction <= CORE_OUTER -> EmotionLevel.CORE
            fraction in MIDDLE_INNER..MIDDLE_OUTER -> EmotionLevel.MIDDLE
            fraction in OUTER_INNER..OUTER_OUTER -> EmotionLevel.OUTER
            else -> return null
        }

        val angle = normalize(angleOf(point, center) - rotation)
        val family = families[floor(angle / FAMILY_SWEEP).toInt().coerceIn(0, FAMILY_COUNT - 1)]

        if (level == EmotionLevel.CORE) return Sector(family, level, 0)

        val withinFamily = angle % FAMILY_SWEEP
        val index = floor(withinFamily / SECTOR_SWEEP).toInt().coerceIn(0, SECTORS_PER_RING - 1)
        return Sector(family, level, index)
    }
}
