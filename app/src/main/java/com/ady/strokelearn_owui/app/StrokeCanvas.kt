package com.ady.strokelearn_owui.app

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.pow

private const val INK_WIDTH = 14f

/* ==================== deviation colouring ==================== */

/** Error below this renders as pure ink — lets small wobble pass unmarked. */
private const val ERROR_DEADZONE = 0.02f

/** Error at or above this renders fully red. */
private const val ERROR_MAX = 0.15f

/** Above 1 keeps the mid-range dark, so only real deviation lights up. */
private const val ERROR_GAMMA = 2.0f

/**
 * Maps a point error to a colour between [ink] and red.
 *
 * Note this is not a log curve: a log is steepest near zero, which would
 * amplify exactly the small deviations we want to ignore. A deadzone plus a
 * power curve above 1 does the opposite, which is what we want.
 */
fun errorColor(error: Float, ink: Color = Color.Black): Color {
    val span = ERROR_MAX - ERROR_DEADZONE
    val raw = ((error - ERROR_DEADZONE) / span).coerceIn(0f, 1f)
    val redness = raw.pow(ERROR_GAMMA)
    return Color(
        red = ink.red + (1f - ink.red) * redness,
        green = ink.green * (1f - redness),
        blue = ink.blue * (1f - redness)
    )
}

/** A drawn stroke resampled and scored point by point against its target. */
data class StrokeComparison(
    val points: List<Point>,   // the drawn stroke, resampled
    val errors: List<Float>,   // distance to the target at each point
    val score: Float
)

/** Compares one stroke and keeps the per-point errors for rendering. */
fun compareStrokeDetailed(drawn: Stroke, target: Stroke, samples: Int = 32): StrokeComparison {
    val a = resample(drawn, samples)
    val b = resample(target, samples)
    if (a.isEmpty() || b.isEmpty()) return StrokeComparison(a, emptyList(), 0f)

    val errors = a.indices.map { distance(a[it], b[it]) }
    val meanError = errors.average().toFloat()
    val score = (1f - meanError / WORST_ERROR).coerceIn(0f, 1f)
    return StrokeComparison(a, errors, score)
}

/** Draws a compared stroke, colouring each segment by its local deviation. */
fun DrawScope.drawComparison(
    comparison: StrokeComparison,
    ink: Color = Color.Black,
    width: Float = INK_WIDTH
) {
    val side = size.minDimension
    val points = comparison.points
    if (points.size < 2 || comparison.errors.size < points.size) return

    for (i in 0 until points.size - 1) {
        // a segment is as wrong as its worse end
        val error = maxOf(comparison.errors[i], comparison.errors[i + 1])
        drawLine(
            color = errorColor(error, ink),
            start = Offset(points[i].x * side, points[i].y * side),
            end = Offset(points[i + 1].x * side, points[i + 1].y * side),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

/* ==================== drawing ==================== */

private fun Stroke.toPath(side: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x * side, points[0].y * side)
    for (i in 1 until points.size) path.lineTo(points[i].x * side, points[i].y * side)
    return path
}

/** Draws one stored stroke. [showStart] marks the first point with a dot. */
fun DrawScope.drawStroke(
    stroke: Stroke,
    color: Color,
    width: Float = INK_WIDTH,
    showStart: Boolean = false
) {
    val side = size.minDimension
    drawPath(
        path = stroke.toPath(side),
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round)
    )
    if (showStart && stroke.points.isNotEmpty()) {
        val first = stroke.points.first()
        drawCircle(color, radius = width, center = Offset(first.x * side, first.y * side))
    }
}

/**
 * Draws the box the glyph occupies, with diagonals, derived from the stored
 * strokes. Gives the user a sense of placement and size in a test, without
 * revealing the shape itself.
 */
fun DrawScope.drawBoundingBox(strokes: List<Stroke>, color: Color) {
    val points = strokes.flatMap { it.points }
    if (points.isEmpty()) return

    val side = size.minDimension
    val left = points.minOf { it.x } * side
    val right = points.maxOf { it.x } * side
    val top = points.minOf { it.y } * side
    val bottom = points.maxOf { it.y } * side

    val topLeft = Offset(left, top)
    val topRight = Offset(right, top)
    val bottomLeft = Offset(left, bottom)
    val bottomRight = Offset(right, bottom)

    drawLine(color, topLeft, topRight, 2f)
    drawLine(color, topRight, bottomRight, 2f)
    drawLine(color, bottomRight, bottomLeft, 2f)
    drawLine(color, bottomLeft, topLeft, 2f)
    drawLine(color, topLeft, bottomRight, 2f)
    drawLine(color, topRight, bottomLeft, 2f)
}
private fun DrawScope.drawGuides(guide: Color) {
    val side = size.minDimension
    drawLine(guide, Offset(side / 2, 0f), Offset(side / 2, side), 2f)
    drawLine(guide, Offset(0f, side / 2), Offset(side, side / 2), 2f)
}
/** Fraction of the canvas the guide glyph's ink should span. */
private const val GUIDE_GLYPH_FILL = 0.85f
/**
 * Draws a character faintly behind the canvas, scaled so its ink spans
 * [GUIDE_GLYPH_FILL] of the drawing area.
 *
 * Uses the native paint's tight text bounds rather than the Compose text layout,
 * because the layout box includes ascent and descent padding — mostly empty air
 * around a single glyph, which would leave it looking far too small.
 */
private fun DrawScope.drawGuideGlyph(glyph: String, tint: Color, typeface: Typeface?) {
    if (glyph.isBlank()) return
    val side = size.minDimension
    val target = side * GUIDE_GLYPH_FILL

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = tint.toArgb()
        textSize = 100f
        typeface?.let { this.typeface = it }
    }

    val bounds = android.graphics.Rect()
    paint.getTextBounds(glyph, 0, glyph.length, bounds)
    val largest = maxOf(bounds.width(), bounds.height())
    if (largest <= 0) return

    paint.textSize = 100f * (target / largest)
    paint.getTextBounds(glyph, 0, glyph.length, bounds)

    val x = (side - bounds.width()) / 2f - bounds.left
    val y = (side - bounds.height()) / 2f - bounds.top
    drawContext.canvas.nativeCanvas.drawText(glyph, x, y, paint)
}

/** Loads a font from assets, or null to use the system font. */
@Composable
fun rememberScriptTypeface(fontFile: String?): Typeface? {
    val context = LocalContext.current
    return remember(fontFile) {
        fontFile?.let {
            runCatching { Typeface.createFromAsset(context.assets, "fonts/$it") }.getOrNull()
        }
    }
}

/* ==================== input canvas ==================== */

/**
 * The square drawing surface used by every mode.
 *
 * @param guideGlyph character rendered faintly behind the strokes, for tracing
 * @param overlay extra drawing below the committed strokes
 */
@Composable
fun DrawingCanvas(
    strokes: List<Stroke>,
    onStrokeFinished: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    inkColor: Color = MaterialTheme.colorScheme.onSurface,
    guideGlyph: String = "",
    guideTypeface: Typeface? = null,
    strongGuide: Boolean = false,
    overlay: DrawScope.() -> Unit = {}
) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var side by remember { mutableFloatStateOf(1f) }

    // derived from the ink so the canvas inverts cleanly in dark mode
    val paper = MaterialTheme.colorScheme.surface
    val guideColor = inkColor.copy(alpha = 0.13f)
    val glyphColor = inkColor.copy(alpha = if (strongGuide) 0.28f else 0.10f)
    val borderColor = inkColor.copy(alpha = 0.20f)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(paper)
            .border(2.dp, borderColor, MaterialTheme.shapes.small)
            .onSizeChanged { side = minOf(it.width, it.height).toFloat() }
            .then(
                if (!enabled) Modifier
                else Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ ->
                            current = current + change.position
                            change.consume()
                        },
                        onDragEnd = {
                            val stroke = Stroke(
                                thinOut(current.map { Point(it.x / side, it.y / side) })
                            )
                            if (stroke.points.size > 1) onStrokeFinished(stroke)
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() }
                    )
                }
            )
    ) {
        drawGuideGlyph(guideGlyph, glyphColor, guideTypeface)
        drawGuides(guideColor)
        overlay()
        strokes.forEach { drawStroke(it, inkColor) }

        if (current.size > 1) {
            val live = Path().apply {
                moveTo(current[0].x, current[0].y)
                current.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                live, inkColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(INK_WIDTH, cap = StrokeCap.Round)
            )
        }
    }
}

/** Drops points closer together than [minGap], to keep saved strokes compact. */
private fun thinOut(points: List<Point>, minGap: Float = 0.02f): List<Point> {
    if (points.size < 2) return points
    val kept = mutableListOf(points.first())
    for (p in points.drop(1)) {
        if (distance(kept.last(), p) >= minGap) kept += p
    }
    if (kept.last() != points.last()) kept += points.last()
    return kept
}

/* ==================== scoring ==================== */

/** Mean error at which a stroke scores zero. Raise it to grade more leniently. */
private const val WORST_ERROR = 0.25f

/** Score for one stroke, 0.0 to 1.0. Compares points in order, so direction matters. */
fun compareStrokes(drawn: Stroke, target: Stroke, samples: Int = 32): Float =
    compareStrokeDetailed(drawn, target, samples).score

/**
 * Scores a whole letter. Strokes are paired in drawing order, and a wrong
 * stroke count scales the result down.
 */
fun compareLetters(drawn: List<Stroke>, target: List<Stroke>): Float {
    if (drawn.isEmpty() || target.isEmpty()) return 0f
    val pairs = minOf(drawn.size, target.size)
    val meanScore = (0 until pairs).map { compareStrokes(drawn[it], target[it]) }
        .average().toFloat()
    return meanScore * pairs / maxOf(drawn.size, target.size)
}

/** Per-stroke comparisons for a whole letter, for colouring and feedback. */
fun compareLettersDetailed(drawn: List<Stroke>, target: List<Stroke>): List<StrokeComparison> {
    val pairs = minOf(drawn.size, target.size)
    return (0 until pairs).map { compareStrokeDetailed(drawn[it], target[it]) }
}

/**
 * Returns [n] points spaced evenly along the stroke's length.
 *
 * This is what makes comparison fair: two people drawing the same shape produce
 * very different numbers of raw points, fast here and slow there. Resampling by
 * arc length strips that out, leaving only the shape.
 */
private fun resample(stroke: Stroke, n: Int): List<Point> {
    val points = stroke.points
    if (points.size < 2) return List(n) { points.firstOrNull() ?: Point(0f, 0f) }

    val cumulative = FloatArray(points.size)
    for (i in 1 until points.size) {
        cumulative[i] = cumulative[i - 1] + distance(points[i - 1], points[i])
    }
    val total = cumulative.last()
    if (total <= 0f) return List(n) { points.first() }

    val step = total / (n - 1)
    val result = ArrayList<Point>(n)
    var seg = 1

    for (i in 0 until n) {
        val targetLength = i * step
        while (seg < points.size - 1 && cumulative[seg] < targetLength) seg++
        val segStart = cumulative[seg - 1]
        val segEnd = cumulative[seg]
        val t = if (segEnd > segStart) {
            ((targetLength - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
        } else 0f
        result += lerp(points[seg - 1], points[seg], t)
    }
    return result
}

private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)

private fun lerp(a: Point, b: Point, t: Float): Point =
    Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/**
 * Small non-interactive rendering of a letter, used in the session summary:
 * the target shape faint underneath, the user's strokes on top.
 */
@Composable
fun StrokeThumbnail(
    target: List<Stroke>,
    drawn: List<Stroke>,
    modifier: Modifier = Modifier
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val targetColor = Color(0xFF00AA00).copy(alpha = 0.35f)

    Canvas(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, ink.copy(alpha = 0.20f), MaterialTheme.shapes.small)
    ) {
        target.forEach { drawStroke(it, targetColor, width = 7f) }
        drawn.forEach { drawStroke(it, ink, width = 5f) }
    }
}