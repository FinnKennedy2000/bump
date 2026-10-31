package com.finn.bump.ui.components

import android.util.TypedValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.finn.bump.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ponytail: proportions are hand-picked keyframes, not anatomy data; an approximation
// for a pleasant picture, never a measurement.
private val KfWeek = floatArrayOf(5f, 8f, 12f, 20f, 30f, 40f)
private val KfHead = floatArrayOf(0.50f, 0.48f, 0.36f, 0.30f, 0.27f, 0.25f)   // head / length
private val KfCurl = floatArrayOf(1.00f, 0.95f, 0.85f, 0.75f, 0.65f, 0.58f)   // 1 = tight C
private val KfArm = floatArrayOf(0f, 0.06f, 0.26f, 0.30f, 0.31f, 0.32f)       // of length
private val KfLeg = floatArrayOf(0f, 0.05f, 0.22f, 0.30f, 0.34f, 0.36f)
private val KfBody = floatArrayOf(0.62f, 0.66f, 0.70f, 0.78f, 0.85f, 0.90f)   // torso half-width / head radius
private val KfSkin = floatArrayOf(0.0f, 0.35f, 0.6f, 0.8f, 0.95f, 1.0f)       // 0 = translucent

private fun at(week: Float, ys: FloatArray): Float {
    val w = week.coerceIn(KfWeek.first(), KfWeek.last())
    val i = (KfWeek.indexOfLast { it <= w }).coerceAtMost(KfWeek.size - 2)
    val f = (w - KfWeek[i]) / (KfWeek[i + 1] - KfWeek[i])
    return ys[i] + (ys[i + 1] - ys[i]) * f
}

private fun Offset.unit(): Offset { val d = getDistance(); return if (d == 0f) this else this / d }
private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3 - 2 * t)
}
private fun gauss(x: Float, c: Float, w: Float): Float { val d = (x - c) / w; return kotlin.math.exp(-d * d) }

/** Open curve through pts, smoothed with quadratic midpoints. */
private fun Path.smooth(pts: List<Offset>, closed: Boolean = false) {
    moveTo(pts[0].x, pts[0].y)
    for (i in 1 until pts.size - 1) {
        val m = (pts[i] + pts[i + 1]) / 2f
        quadraticTo(pts[i].x, pts[i].y, m.x, m.y)
    }
    lineTo(pts.last().x, pts.last().y)
    if (closed) close()
}

@Composable
fun FetusRender(week: Int, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val glow = if (dark) Palette.ClayDark else Palette.Clay
    val skinOpaque = if (dark) Color(0xFFE9A88C) else Color(0xFFEEA489)
    val skinSheer = if (dark) Color(0xFF8A5A52) else Color(0xFFF6CFC2)
    val shade = if (dark) Color(0xFF6B3F36) else Color(0xFFC77D63)
    val placentaCol = if (dark) Color(0xFF9C4B45) else Color(0xFFC4675B)
    val desc = "Illustration of baby at $week weeks, approximate"

    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = desc }) {
        val m = min(size.width, size.height)
        val c = Offset(size.width / 2, size.height / 2)
        // Amniotic sac glow.
        drawCircle(
            Brush.radialGradient(
                listOf(glow.copy(alpha = 0.26f), glow.copy(alpha = 0.10f), Color.Transparent),
                center = c, radius = m / 2,
            ),
            radius = m / 2, center = c,
        )
        if (week < 5) {
            val r = m * (0.015f + 0.01f * week.coerceAtLeast(0))
            drawCircle(
                Brush.radialGradient(listOf(skinOpaque, shade), center = c, radius = r),
                radius = r, center = c,
            )
            return@Canvas
        }

        // Everything below is built in unit space (crown-rump length = 1), then scaled to fit.
        val w = week.toFloat()
        val R = at(w, KfHead) / 2
        val S = 1f - R                                   // spine length, head centre to rump
        val bend = (at(w, KfCurl) * 190.0 + 10.0) * PI / 180.0
        val a0 = PI / 2 - 0.5
        val skin = lerp(skinSheer, skinOpaque, at(w, KfSkin))
        val skinFar = lerp(skin, shade, 0.3f)
        val n = 40
        val sp = ArrayList<Offset>(n + 1)
        var px = 0f; var py = 0f
        for (i in 0..n) {
            sp.add(Offset(px, py))
            val a = a0 + bend * (i + 0.5) / n
            px += (cos(a) * S / n).toFloat(); py += (sin(a) * S / n).toFloat()
        }
        fun ang(t: Float) = a0 + bend * t                                  // t in 0..1 along spine
        fun tan(t: Float) = Offset(cos(ang(t)).toFloat(), sin(ang(t)).toFloat())
        fun inn(t: Float) = Offset(-sin(ang(t)).toFloat(), cos(ang(t)).toFloat())  // toward belly
        fun spine(t: Float): Offset {
            val f = t.coerceIn(0f, 1f) * n
            val i = f.toInt().coerceAtMost(n - 1)
            return sp[i] + (sp[i + 1] - sp[i]) * (f - i)
        }
        val t0 = (R * 0.6f) / S
        val bodyHalf = R * at(w, KfBody)
        fun half(t: Float): Float {
            val u = ((t - t0) / (1f - t0)).coerceIn(0f, 1f)
            val p = 0.55f + 0.45f * smoothstep(0f, 0.35f, u) - 0.4f * smoothstep(0.62f, 1f, u)
            return bodyHalf * p
        }

        // Torso outline, one smooth closed path.
        val steps = 24
        val outer = ArrayList<Offset>(); val inner = ArrayList<Offset>()
        for (i in 0..steps) {
            val t = t0 + (1f - t0) * i / steps
            val bulge = 1f + 0.12f * sin(PI * i / steps).toFloat()          // soft belly
            outer.add(spine(t) - inn(t) * half(t))
            inner.add(spine(t) + inn(t) * half(t) * bulge)
        }
        val torso = Path().apply { smooth(outer + inner.reversed(), closed = true) }
        val hc = spine(0f)
        val F = inn(0f)                                   // face direction
        val U = tan(0f) * -1f                             // crown direction
        val faceT = smoothstep(7f, 12f, w)
        val head = Path().apply {
            val pts = ArrayList<Offset>()
            val k = 96
            for (i in 0 until k) {
                val th = (2 * PI * i / k).toFloat()
                var d = th * 180f / PI.toFloat(); if (d > 180f) d -= 360f
                val r = R * (1f +
                    faceT * (0.09f * gauss(d, 0f, 9f) + 0.05f * gauss(d, -34f, 10f) +
                        0.035f * gauss(d, 33f, 12f) - 0.035f * gauss(d, 16f, 7f) -
                        0.03f * gauss(d, -15f, 6f)) +
                    0.04f * gauss(d, 175f, 40f) - 0.05f * gauss(d, -85f, 18f))
                pts.add(hc + F * (r * cos(th)) + U * (r * sin(th)))
            }
            smooth(pts + pts[0] + pts[1], closed = true)
        }
        val cap = Path().apply { addOval(androidx.compose.ui.geometry.Rect(spine(1f), half(1f))) }
        val body = Path.combine(PathOperation.Union, Path.combine(PathOperation.Union, torso, head), cap)

        // Limb endpoints.
        val wd = R * 0.36f
        val shoulder = spine(t0 + (1f - t0) * 0.18f).let { it + inn(t0 + (1f - t0) * 0.18f) * (half(0.2f) * 0.5f) }
        val hip = spine(0.97f) + inn(0.97f) * (half(0.97f) * 0.3f)
        val armL = at(w, KfArm); val legL = at(w, KfLeg)

        class Limb(val a: Offset, val b: Offset, val c: Offset)
        fun twoBone(s: Offset, target: Offset, l1: Float, l2: Float, away: Offset): Limb {
            val d = (target - s).getDistance().coerceIn(0.001f, l1 + l2 - 0.001f)
            val dir = (target - s).unit()
            val h = kotlin.math.sqrt((l1 * l1 - (d / 2) * (d / 2)).coerceAtLeast(0f).toDouble()).toFloat()
            val perp = Offset(-dir.y, dir.x)
            val mid = s + dir * (d / 2)
            val e1 = mid + perp * h; val e2 = mid - perp * h
            val e = if ((e1 - s).let { it.x * away.x + it.y * away.y } > (e2 - s).let { it.x * away.x + it.y * away.y }) e1 else e2
            return Limb(s, e, s + dir * d)
        }
        fun digits(p: Offset, dir: Offset, len: Float, cnt: Int, spread: Float) {
            val base = atan2(dir.y, dir.x)
            for (j in 0 until cnt) {
                val aj = base + (j - (cnt - 1) / 2f) * spread
                drawLine(
                    shade.copy(alpha = 0.85f), p,
                    p + Offset(cos(aj) * len, sin(aj) * len), wd * 0.16f, StrokeCap.Round,
                )
            }
        }
        fun stroke2(a: Offset, b: Offset, wdt: Float, col: Color) {
            drawLine(shade.copy(alpha = 0.55f), a, b, wdt + R * 0.04f, StrokeCap.Round)
            drawLine(col, a, b, wdt, StrokeCap.Round)
        }

        val isNear = listOf(false, true)
        val legs = isNear.map { near ->
            val dir = (inn(0.97f) * 0.9f + tan(0.97f) * -0.5f + (if (near) Offset.Zero else tan(0.97f) * 0.4f)).unit()
            val knee = hip + dir * (legL / 2)
            val shin = (dir * -0.5f + tan(0.97f) * (if (near) 0.9f else 0.6f)).unit()
            val ankle = knee + shin * (legL / 2)
            Triple(near, knee, ankle) to Offset(shin.x, shin.y)
        }
        val armT = hc + F * (R * 0.75f) - U * (R * 0.8f)
        val arms = isNear.map { near ->
            val tgt = if (near) armT else armT + U * (R * 0.35f) - F * (R * 0.2f)
            near to twoBone(shoulder, tgt, armL / 2, armL / 2, inn(0.2f))
        }

        // Fit all geometry into ~80% of the frame.
        val allPts = ArrayList<Offset>()
        allPts += outer; allPts += inner
        allPts += listOf(hc + Offset(R * 1.15f, R * 1.15f), hc - Offset(R * 1.15f, R * 1.15f))
        allPts += legs.flatMap { listOf(it.first.second, it.first.third) }
        allPts += arms.flatMap { listOf(it.second.b, it.second.c) }
        val cordStart = spine(t0 + (1f - t0) * 0.5f) + inn(0.5f) * (half(0.5f) * 1.1f)
        val plac = cordStart + inn(0.5f) * 0.36f + tan(0.5f) * 0.12f
        if (w >= 6) { allPts += plac + Offset(0.15f, 0.1f); allPts += plac - Offset(0.15f, 0.1f) }
        val x0 = allPts.minOf { it.x }; val x1 = allPts.maxOf { it.x }
        val y0 = allPts.minOf { it.y }; val y1 = allPts.maxOf { it.y }
        val frac = 0.62f + 0.20f * smoothstep(5f, 12f, w)
        val sc = frac * m / maxOf(x1 - x0, y1 - y0)
        val ctr = Offset((x0 + x1) / 2, (y0 + y1) / 2)

        withTransform({ translate(c.x, c.y); scale(sc, sc, Offset.Zero); translate(-ctr.x, -ctr.y) }) {
            // Placenta and cord, behind the baby.
            if (w >= 6) {
                withTransform({ rotate(-20f, plac) }) {
                    drawOval(
                        Brush.radialGradient(
                            listOf(lerp(placentaCol, Color.White, 0.2f), placentaCol), center = plac, radius = 0.15f),
                        plac - Offset(0.15f, 0.09f), Size(0.30f, 0.18f), alpha = 0.9f,
                    )
                }
                val cordPath = Path().apply {
                    moveTo(cordStart.x, cordStart.y)
                    cubicTo(
                        cordStart.x + inn(0.5f).x * 0.15f + 0.08f, cordStart.y + inn(0.5f).y * 0.15f + 0.1f,
                        plac.x - 0.1f, plac.y - 0.08f, plac.x, plac.y,
                    )
                }
                drawPath(cordPath, lerp(skinSheer, placentaCol, 0.35f), style = Stroke(0.026f, cap = StrokeCap.Round))
                drawPath(cordPath, Color.White.copy(alpha = 0.25f), style = Stroke(0.008f, cap = StrokeCap.Round))
            }
            // Far limbs first.
            legs.filter { !it.first.first }.forEach { (l, shin) ->
                stroke2(hip, l.second, wd * 1.15f, skinFar); stroke2(l.second, l.third, wd, skinFar)
                stroke2(l.third, l.third + shin.rotated(1.3f) * (wd * 0.9f), wd * 0.7f, skinFar)
            }
            arms.filter { !it.first }.forEach { (_, a) ->
                stroke2(a.a, a.b, wd, skinFar); stroke2(a.b, a.c, wd * 0.9f, skinFar)
                drawCircle(skinFar, wd * 0.6f, a.c)
            }
            // Body with soft shading and a darker rim.
            val mid = spine(0.5f) + inn(0.5f) * (bodyHalf * 0.4f)
            drawPath(body, Brush.radialGradient(
                listOf(lerp(skin, Color.White, 0.25f), skin, lerp(skin, shade, 0.5f)),
                center = mid, radius = 0.75f))
            clipPath(body) {
                drawPath(body, shade.copy(alpha = 0.45f), style = Stroke(R * 0.28f))
            }
            drawPath(body, shade.copy(alpha = 0.5f), style = Stroke(0.006f))
            // Face: closed eye, ear.
            if (w >= 10) {
                val e = hc + F * (R * 0.5f) + U * (R * 0.2f)
                val eyeA = atan2(F.y, F.x) * 180f / PI.toFloat()
                withTransform({ rotate(eyeA + 90f, e) }) {
                    drawArc(
                        shade, 20f, 140f, false, topLeft = e - Offset(R * 0.14f, R * 0.07f),
                        size = Size(R * 0.28f, R * 0.14f), style = Stroke(R * 0.045f, cap = StrokeCap.Round),
                    )
                }
                drawCircle(shade.copy(alpha = 0.35f), R * 0.16f, hc - F * (R * 0.15f) - U * (R * 0.1f))
            }
            // Near leg, then near arm and hand on top.
            legs.filter { it.first.first }.forEach { (l, shin) ->
                stroke2(hip, l.second, wd * 1.15f, skin); stroke2(l.second, l.third, wd, skin)
                val foot = l.third + shin.rotated(1.3f) * (wd * 0.9f)
                stroke2(l.third, foot, wd * 0.7f, skin)
                if (w >= 10) digits(foot, shin.rotated(1.3f), wd * 0.5f, 5, 0.3f)
            }
            arms.filter { it.first }.forEach { (_, a) ->
                stroke2(a.a, a.b, wd, skin); stroke2(a.b, a.c, wd * 0.9f, skin)
                drawCircle(shade.copy(alpha = 0.55f), wd * 0.66f, a.c)
                drawCircle(skin, wd * 0.6f, a.c)
                if (w >= 10) digits(a.c, a.c - a.b, wd * 0.55f, 4, 0.4f)
            }
        }
    }
}

private fun Offset.rotated(rad: Float): Offset =
    Offset(x * cos(rad) - y * sin(rad), x * sin(rad) + y * cos(rad))


@Composable
fun LifeSizeHandFoot(handMm: Float?, footMm: Float?, modifier: Modifier = Modifier) {
    if (handMm == null && footMm == null) return
    val dm = LocalContext.current.resources.displayMetrics
    fun mmPx(mm: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm, dm)
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val line = if (dark) Palette.ClayDark else Palette.Clay
    val fill = if (dark) Palette.ClayTintDark else Palette.ClayTintLight
    val gap = mmPx(8f)
    val items = listOfNotNull(
        handMm?.let { Triple("Hand", it, true) },
        footMm?.let { Triple("Foot", it, false) },
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val avail = constraints.maxWidth.toFloat()
        // Widths: hand .64 L, foot .46 L.
        val total = items.sumOf { (if (it.third) 0.64f else 0.46f).times(mmPx(it.second)).toDouble() }
            .toFloat() + gap * (items.size - 1)
        val scale = if (total > avail) avail / total else 1f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(with(density) { (gap * scale).toDp() }, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { (name, mm, isHand) ->
                val l = mmPx(mm) * scale
                val wPx = l * if (isHand) 0.64f else 0.46f
                val label = "$name ≈ ${mm.toInt()} mm · " + if (scale < 1f) "scaled" else "life size"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val wDp = with(density) { wPx.toDp() }
                    Canvas(
                        Modifier.size(wDp, with(density) { l.toDp() })
                            .semantics { contentDescription = "$name outline, $label" },
                    ) {
                        val sw = 1.5.dp.toPx()
                        val shapes = if (isHand) handShapes(l) else footShapes(l)
                        // Outlines first, fills over them, so overlaps do not show seams.
                        shapes.forEach { it.draw(this, line, sw * 2) }
                        shapes.forEach { it.draw(this, fill, 0f) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(maxOf(wDp, 96.dp)),
                    )
                }
            }
        }
    }
}

/** A round-capped line (d wide) or, with x1 == x2 && y1 == y2, a dot; or a rounded rect. */
private class Shape(
    val x1: Float, val y1: Float, val x2: Float, val y2: Float, val d: Float,
    val rect: Boolean = false,
) {
    fun draw(s: DrawScope, color: Color, extra: Float) = with(s) {
        if (rect) {
            val r = d / 2 // corner radius
            drawRoundRect(
                color, Offset(x1 - extra / 2, y1 - extra / 2),
                Size(x2 - x1 + extra, y2 - y1 + extra), CornerRadius(r + extra / 2),
            )
        } else drawLine(color, Offset(x1, y1), Offset(x2, y2), d + extra, StrokeCap.Round)
    }
}

private fun handShapes(l: Float): List<Shape> {
    val d = 0.095f * l
    fun f(x: Float, tip: Float) = Shape(x * l, (tip + 0.0475f) * l, x * l, 0.5f * l, d)
    return listOf(
        Shape(0.14f * l, 0.40f * l, 0.58f * l, l, 0.2f * l, rect = true),
        f(0.195f, 0.10f), f(0.305f, 0.0f), f(0.415f, 0.07f), f(0.525f, 0.22f),
        Shape(0.17f * l, 0.62f * l, 0.06f * l, 0.38f * l, 0.1f * l),
    )
}

private fun footShapes(l: Float): List<Shape> = listOf(
    Shape(0.03f * l, 0.30f * l, 0.43f * l, 0.80f * l, 0.3f * l, rect = true),
    Shape(0.21f * l, 0.84f * l, 0.24f * l, 0.87f * l, 0.26f * l),
    Shape(0.12f * l, 0.27f * l, 0.12f * l, 0.12f * l, 0.13f * l),
    Shape(0.225f * l, 0.30f * l, 0.225f * l, 0.20f * l, 0.09f * l),
    Shape(0.30f * l, 0.32f * l, 0.30f * l, 0.24f * l, 0.085f * l),
    Shape(0.36f * l, 0.34f * l, 0.36f * l, 0.28f * l, 0.075f * l),
    Shape(0.405f * l, 0.36f * l, 0.405f * l, 0.32f * l, 0.065f * l),
)

@Preview(showBackground = true)
@Composable
private fun FetusRenderPreview() {
    Column {
        FetusRender(8, Modifier.size(160.dp))
        FetusRender(24, Modifier.size(160.dp))
        FetusRender(40, Modifier.size(160.dp))
        LifeSizeHandFoot(handMm = 32f, footMm = 28f)
    }
}
