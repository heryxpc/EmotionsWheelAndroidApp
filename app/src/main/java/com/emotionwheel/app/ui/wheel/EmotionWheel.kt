package com.emotionwheel.app.ui.wheel

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.emotionwheel.app.R
import com.emotionwheel.app.data.catalog.Emotion
import com.emotionwheel.app.data.catalog.EmotionCatalog
import com.emotionwheel.app.data.catalog.EmotionLevel
import com.emotionwheel.app.ui.theme.contentColorFor
import com.emotionwheel.app.ui.theme.palette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The type size each ring uses. One size per ring, not per word: sizing every label
 * to its own sector makes a ring look ragged, since "ira" would tower over
 * "deslumbramiento" right next to it.
 */
private data class RingTypography(val core: Float, val middle: Float, val outer: Float) {
    fun forLevel(level: Int): Float = when (level) {
        EmotionLevel.CORE -> core
        EmotionLevel.MIDDLE -> middle
        else -> outer
    }
}

/**
 * Largest size at which every label of a ring still fits its sector, measured once per
 * layout rather than on every frame of a spin.
 */
private fun computeRingTypography(
    catalog: EmotionCatalog,
    radius: Float,
    paint: Paint,
): RingTypography {
    fun fittingSize(level: Int, labels: List<String>, cap: Float): Float {
        val inner = radius * labelInnerFraction(level)
        val outer = radius * labelOuterFraction(level)
        val mid = (inner + outer) / 2f
        val sweep = WheelGeometry.sweep(level)

        val availableLength = (outer - inner) * 0.92f
        val availableHeight = 2f * PI.toFloat() * mid * (sweep / 360f) * 0.78f

        paint.textSize = minOf(cap, availableHeight)
        val startingSize = paint.textSize
        val widest = labels.maxOf { paint.measureText(it) }
        return if (widest > availableLength) startingSize * availableLength / widest
        else startingSize
    }

    val families = WheelGeometry.families
    return RingTypography(
        core = fittingSize(
            EmotionLevel.CORE,
            families.map { catalog.core(it).label.uppercase() },
            radius * 0.075f,
        ),
        middle = fittingSize(
            EmotionLevel.MIDDLE,
            families.flatMap { catalog.ring(it, EmotionLevel.MIDDLE).map { e -> e.label.uppercase() } },
            radius * 0.055f,
        ),
        outer = fittingSize(
            EmotionLevel.OUTER,
            families.flatMap { catalog.ring(it, EmotionLevel.OUTER).map { e -> e.label.uppercase() } },
            radius * 0.055f,
        ),
    )
}

/** Where a label starts and ends along the radius, inset from its sector's edges. */
private fun labelInnerFraction(level: Int): Float =
    if (level == EmotionLevel.CORE) 0.155f
    else WheelGeometry.innerRadiusFraction(level) + 0.015f

private fun labelOuterFraction(level: Int): Float =
    if (level == EmotionLevel.CORE) WheelGeometry.CORE_OUTER - 0.01f
    else WheelGeometry.outerRadiusFraction(level) - 0.015f

/**
 * The emotion wheel: six families, three rings, ninety words.
 *
 * Tapping picks a word. Dragging spins the whole wheel, which is what makes the outer
 * ring comfortable to read and to hit — the labels there are narrow, so being able to
 * bring one down to the thumb matters more than it sounds.
 *
 * Everything is drawn on one Canvas: with ninety sectors, a composable per sector
 * would cost far more than the arithmetic does.
 */
@Composable
fun EmotionWheel(
    catalog: EmotionCatalog,
    selected: Emotion?,
    onSelect: (Emotion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    val emojiPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
    }

    // Announce the current pick to screen readers without redrawing anything.
    val description = stringResource(R.string.wheel_content_description)

    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val radiusPx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() } / 2f
        val typography = remember(radiusPx, catalog) {
            computeRingTypography(catalog, radiusPx, labelPaint)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(catalog) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val slop = viewConfiguration.touchSlop

                        var travelled = 0f
                        var dragging = false
                        var previousAngle = WheelGeometry.angleOf(down.position, center)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                if (!dragging) {
                                    val radius = min(center.x, center.y)
                                    val sector = WheelGeometry.sectorAt(
                                        point = change.position,
                                        center = center,
                                        radius = radius,
                                        rotation = rotation.value,
                                    )
                                    sector?.let {
                                        val emotion = catalog.ring(it.family, it.level)
                                            .getOrNull(it.index)
                                            ?: catalog.core(it.family)
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelect(emotion)
                                    }
                                    change.consume()
                                }
                                isDragging = false
                                break
                            }

                            travelled += (change.position - change.previousPosition).getDistance()
                            if (!dragging && travelled > slop) {
                                dragging = true
                                isDragging = true
                            }
                            if (dragging) {
                                val angle = WheelGeometry.angleOf(change.position, center)
                                val delta = shortestDelta(previousAngle, angle)
                                previousAngle = angle
                                scope.launch { rotation.snapTo(rotation.value + delta) }
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            val center = this.center
            val radius = min(size.width, size.height) / 2f
            val selectionColor = Color.White

            rotate(degrees = rotation.value, pivot = center) {
                WheelGeometry.families.forEach { family ->
                    val palette = family.palette

                    // Core wedge with the family name and its face.
                    val core = catalog.core(family)
                    drawWedge(
                        center = center,
                        radius = radius,
                        level = EmotionLevel.CORE,
                        startAngle = WheelGeometry.startAngle(family, EmotionLevel.CORE, 0),
                        color = palette.core,
                        selected = selected?.id == core.id,
                        selectionColor = selectionColor,
                    )

                    listOf(EmotionLevel.MIDDLE, EmotionLevel.OUTER).forEach { level ->
                        catalog.ring(family, level).forEachIndexed { index, emotion ->
                            drawWedge(
                                center = center,
                                radius = radius,
                                level = level,
                                startAngle = WheelGeometry.startAngle(family, level, index),
                                color = palette.forLevel(level),
                                selected = selected?.id == emotion.id,
                                selectionColor = selectionColor,
                            )
                        }
                    }
                }

                // Labels go in a second pass so no wedge ever paints over its neighbor's text.
                WheelGeometry.families.forEach { family ->
                    val palette = family.palette

                    val core = catalog.core(family)
                    drawRadialLabel(
                        text = core.label.uppercase(),
                        center = center,
                        radius = radius,
                        level = EmotionLevel.CORE,
                        angle = WheelGeometry.midAngle(family, EmotionLevel.CORE, 0),
                        wheelRotation = rotation.value,
                        textSize = typography.core,
                        color = contentColorFor(palette.core),
                        paint = labelPaint,
                    )
                    drawEmoji(
                        emoji = family.emoji,
                        center = center,
                        radius = radius,
                        angle = WheelGeometry.midAngle(family, EmotionLevel.CORE, 0),
                        wheelRotation = rotation.value,
                        paint = emojiPaint,
                    )

                    listOf(EmotionLevel.MIDDLE, EmotionLevel.OUTER).forEach { level ->
                        catalog.ring(family, level).forEachIndexed { index, emotion ->
                            drawRadialLabel(
                                text = emotion.label.uppercase(),
                                center = center,
                                radius = radius,
                                level = level,
                                angle = WheelGeometry.midAngle(family, level, index),
                                wheelRotation = rotation.value,
                                textSize = typography.forLevel(level),
                                color = contentColorFor(palette.forLevel(level)),
                                paint = labelPaint,
                            )
                        }
                    }
                }
            }
        }
    }

    // Ease the wheel back to rest once the user stops spinning it, so the labels
    // return to the orientation of the printed original.
    LaunchedEffect(isDragging) {
        if (!isDragging && rotation.value != 0f) {
            // Snap back only when the wheel came to rest near its printed orientation,
            // measured the short way round so 359 degrees counts as one, not as 359.
            val offBy = abs(shortestDelta(0f, WheelGeometry.normalize(rotation.value)))
            if (offBy < SETTLE_THRESHOLD_DEG) rotation.animateTo(0f)
        }
    }
}

private const val SETTLE_THRESHOLD_DEG = 12f

/** Signed shortest way from [from] to [to], so crossing twelve o'clock is not a 359 degree jump. */
private fun shortestDelta(from: Float, to: Float): Float {
    val raw = to - from
    return when {
        raw > 180f -> raw - 360f
        raw < -180f -> raw + 360f
        else -> raw
    }
}

/**
 * Fills one sector. Rings are drawn as a thick stroked arc rather than a path: with 90
 * sectors redrawn on every frame of a spin, avoiding a Path allocation per sector keeps
 * the drag smooth.
 */
private fun DrawScope.drawWedge(
    center: Offset,
    radius: Float,
    level: Int,
    startAngle: Float,
    color: Color,
    selected: Boolean,
    selectionColor: Color,
) {
    val sweep = WheelGeometry.sweep(level)
    val gap = if (level == EmotionLevel.CORE) 0.8f else 0.55f
    val start = WheelGeometry.toSweepStart(startAngle + gap / 2f)
    val drawnSweep = sweep - gap

    if (level == EmotionLevel.CORE) {
        val outer = radius * WheelGeometry.CORE_OUTER
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = drawnSweep,
            useCenter = true,
            topLeft = Offset(center.x - outer, center.y - outer),
            size = Size(outer * 2, outer * 2),
        )
    } else {
        val inner = radius * WheelGeometry.innerRadiusFraction(level)
        val outer = radius * WheelGeometry.outerRadiusFraction(level)
        val mid = (inner + outer) / 2f
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = drawnSweep,
            useCenter = false,
            topLeft = Offset(center.x - mid, center.y - mid),
            size = Size(mid * 2, mid * 2),
            style = Stroke(width = outer - inner),
        )
    }

    if (selected) {
        drawSelectionOutline(center, radius, level, startAngle, selectionColor)
    }
}

/** The chosen word gets a bright outline; only one path is built per frame. */
private fun DrawScope.drawSelectionOutline(
    center: Offset,
    radius: Float,
    level: Int,
    startAngle: Float,
    color: Color,
) {
    val inner = radius * WheelGeometry.innerRadiusFraction(level)
    val outer = radius * WheelGeometry.outerRadiusFraction(level)
    val sweep = WheelGeometry.sweep(level)
    val start = WheelGeometry.toSweepStart(startAngle)

    val path = Path().apply {
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(center, outer),
            startAngleDegrees = start,
            sweepAngleDegrees = sweep,
            forceMoveTo = true,
        )
        if (inner > 0f) {
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(center, inner),
                startAngleDegrees = start + sweep,
                sweepAngleDegrees = -sweep,
                forceMoveTo = false,
            )
        } else {
            lineTo(center.x, center.y)
        }
        close()
    }
    drawPath(path, color, style = Stroke(width = radius * 0.012f))
}

/**
 * Draws a label along the radius of its sector, at the size its ring settled on.
 * Labels on the left half are flipped so no word reads upside down — including after
 * the user spins the wheel, which is why [wheelRotation] is folded into the decision.
 */
private fun DrawScope.drawRadialLabel(
    text: String,
    center: Offset,
    radius: Float,
    level: Int,
    angle: Float,
    wheelRotation: Float,
    textSize: Float,
    color: Color,
    paint: Paint,
) {
    val inner = radius * labelInnerFraction(level)
    val outer = radius * labelOuterFraction(level)
    val mid = (inner + outer) / 2f

    paint.color = color.toArgb()
    paint.textSize = textSize

    // On screen the label sits at `angle + wheelRotation`; past the bottom it would
    // read right to left, so it is turned around in place.
    val onScreenAngle = WheelGeometry.normalize(angle + wheelRotation)
    val flipped = onScreenAngle > 180f

    drawContext.canvas.nativeCanvas.apply {
        val checkpoint = save()
        rotate(angle - 90f, center.x, center.y)
        if (flipped) rotate(180f, center.x + mid, center.y)
        val baseline = center.y - (paint.ascent() + paint.descent()) / 2f
        drawText(text, center.x + mid, baseline, paint)
        restoreToCount(checkpoint)
    }
}

/** The family face, sitting between the hub and the family name. */
private fun DrawScope.drawEmoji(
    emoji: String,
    center: Offset,
    radius: Float,
    angle: Float,
    wheelRotation: Float,
    paint: Paint,
) {
    paint.textSize = radius * 0.062f
    val distance = radius * 0.093f
    val onScreen = Math.toRadians((angle + wheelRotation).toDouble())
    val x = center.x + distance * sin(onScreen).toFloat()
    val y = center.y - distance * cos(onScreen).toFloat()
    val baseline = y - (paint.ascent() + paint.descent()) / 2f

    // Drawn without the wheel's rotation so the faces stay upright while it spins.
    drawContext.canvas.nativeCanvas.apply {
        val checkpoint = save()
        rotate(-wheelRotation, center.x, center.y)
        drawText(emoji, x, baseline, paint)
        restoreToCount(checkpoint)
    }
}
