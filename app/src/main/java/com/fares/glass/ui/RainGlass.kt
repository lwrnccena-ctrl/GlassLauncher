package com.fares.glass.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Something the rain can hit: the dock, or an app tile. [id] must stay the same from frame to frame. */
class RainSurface(val id: Int, val rect: Rect, val corner: Float)

// Wind: how far a streak moves sideways for every pixel it falls.
private const val LEAN = 0.04f

private class Streak {
    var x = 0f
    var y = 0f
    var speed = 0f
    var len = 0f
    var width = 0f
    var alpha = 0f
}

/** A drop sitting on a surface. (u, v) is its place measured from the surface's top-left corner. */
private class Bead(
    val surface: Int,
    var u: Float,
    var v: Float,
    var r: Float,
    val rSlide: Float,
    val life: Float,
) {
    var age = 0f
    var sliding = false
    var vy = 0f
    var trailFrom = v
    var pause = 0f
}

/** A tiny flying droplet: a splash, or a drop that fell off the bottom of a surface. */
private class Spark {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var r = 1f
}

private class RainSim {
    private val streaks = ArrayList<Streak>()
    private val beads = ArrayList<Bead>()
    private val sparks = ArrayList<Spark>()
    private val byId = HashMap<Int, RainSurface>()
    private var w = 0f
    private var h = 0f

    fun step(dt0: Float, w: Float, h: Float, u: Float, surfaces: List<RainSurface>) {
        val dt = dt0.coerceIn(0f, 0.05f)
        this.w = w
        this.h = h
        byId.clear()
        for (s in surfaces) byId[s.id] = s
        fillStreaks(u)
        stepStreaks(dt, u, surfaces)
        drizzle(dt, u, surfaces)
        stepBeads(dt, u)
        stepSparks(dt, u)
    }

    // ---- Falling streaks (three depths: far = small and faint, near = long and bright) ----

    private fun fillStreaks(u: Float) {
        val n = ((w * h) / (u * u) / (960f * 540f) * 150f).toInt().coerceIn(60, 240)
        while (streaks.size < n) streaks.add(Streak().also { spawn(it, u, true) })
        while (streaks.size > n) streaks.removeAt(streaks.size - 1)
    }

    private fun spawn(s: Streak, u: Float, anywhere: Boolean) {
        val layer = Random.nextFloat()
        when {
            layer < 0.50f -> { s.speed = 520f; s.len = 9f; s.width = 0.5f; s.alpha = 0.22f }
            layer < 0.85f -> { s.speed = 800f; s.len = 17f; s.width = 0.8f; s.alpha = 0.35f }
            else -> { s.speed = 1150f; s.len = 30f; s.width = 1.2f; s.alpha = 0.50f }
        }
        val v = 0.85f + Random.nextFloat() * 0.3f
        s.speed *= u * v
        s.len *= u * v
        s.width = max(1f, s.width * u)
        s.x = Random.nextFloat() * (w + 40f * u) - 20f * u
        s.y = if (anywhere) Random.nextFloat() * h - s.len else -s.len - Random.nextFloat() * 40f * u
    }

    private fun stepStreaks(dt: Float, u: Float, surfaces: List<RainSurface>) {
        for (s in streaks) {
            val py = s.y
            s.y += s.speed * dt
            s.x += s.speed * LEAN * dt
            var respawn = false
            var hit: RainSurface? = null
            for (sf in surfaces) {
                val r = sf.rect
                if (s.x < r.left || s.x > r.right) continue
                // Coming down onto the top edge: it hits. Already inside (the surface just appeared): drop it quietly.
                if (py < r.top && s.y >= r.top) { hit = sf; break }
                if (s.y > r.top && s.y < r.bottom) { respawn = true; break }
            }
            if (hit != null) {
                val r = hit.rect
                splash(s.x, r.top, u, s.alpha)
                if (Random.nextFloat() < 0.55f) {
                    val m = hit.corner * 0.5f
                    val x = (s.x - r.left).coerceIn(m, (r.width - m).coerceAtLeast(m))
                    addBead(hit, x, (1f + Random.nextFloat() * 5f) * u, (0.9f + Random.nextFloat() * 0.7f) * u, u)
                }
                respawn = true
            }
            if (respawn || s.y - s.len > h || s.x > w + 40f * u) spawn(s, u, false)
        }
    }

    private fun splash(x: Float, y: Float, u: Float, power: Float) {
        if (sparks.size > 220) return
        repeat(2 + Random.nextInt(3)) {
            val sp = Spark()
            sp.x = x
            sp.y = y
            sp.vx = (Random.nextFloat() - 0.5f) * 220f * u
            sp.vy = -(50f + Random.nextFloat() * 130f) * u * (0.6f + power)
            sp.maxLife = 0.20f + Random.nextFloat() * 0.18f
            sp.life = sp.maxLife
            sp.r = (0.5f + Random.nextFloat() * 0.6f) * u
            sparks.add(sp)
        }
    }

    // ---- Drops sitting on the glass ----

    /** Small drops also appear anywhere on a surface, like rain that is blown against a window. */
    private fun drizzle(dt: Float, u: Float, surfaces: List<RainSurface>) {
        for (sf in surfaces) {
            val r = sf.rect
            val areaDp = r.width * r.height / (u * u)
            val n = areaDp / 100_000f * 3f * dt   // about 3 new drops per second on a 100,000 dp² surface
            val count = n.toInt() + if (Random.nextFloat() < n - n.toInt()) 1 else 0
            repeat(count) {
                val m = sf.corner * 0.5f
                val x = m + Random.nextFloat() * (r.width - 2f * m).coerceAtLeast(1f)
                val y = m * 0.6f + Random.nextFloat() * (r.height - m * 1.2f).coerceAtLeast(1f)
                addBead(sf, x, y, (0.6f + Random.nextFloat() * 1.0f) * u, u)
            }
        }
    }

    private fun addBead(sf: RainSurface, x: Float, y: Float, r: Float, unit: Float) {
        // A drop that lands on another one joins it (the area adds up).
        for (b in beads) {
            if (b.surface != sf.id || b.sliding) continue
            val dx = b.u - x
            val dy = b.v - y
            val rr = b.r + r
            if (dx * dx + dy * dy < rr * rr * 0.55f) {
                val a1 = b.r * b.r
                val a2 = r * r
                b.u = (b.u * a1 + x * a2) / (a1 + a2)
                b.v = (b.v * a1 + y * a2) / (a1 + a2)
                b.r = sqrt(a1 + a2)
                return
            }
        }
        // Too many drops: the oldest one starts to run.
        if (beads.size >= 160) {
            beads.firstOrNull { !it.sliding }?.let {
                it.sliding = true
                it.trailFrom = it.v
            }
        }
        beads.add(
            Bead(
                sf.id, x, y, r,
                rSlide = (2.6f + Random.nextFloat() * 1.2f) * unit,
                life = 4f + Random.nextFloat() * 5f,
            ),
        )
    }

    private fun stepBeads(dt: Float, u: Float) {
        var i = 0
        while (i < beads.size) {
            val b = beads[i]
            val sf = byId[b.surface]
            if (sf == null) {
                // The surface is gone (for example the dock faded out): so are its drops.
                beads.removeAt(i)
                continue
            }
            b.age += dt
            if (!b.sliding) {
                // Big enough, or old enough: the drop starts to run down.
                if (b.r >= b.rSlide || b.age > b.life) {
                    b.sliding = true
                    b.trailFrom = b.v
                    b.vy = 0f
                }
            } else {
                if (b.pause > 0f) {
                    b.pause -= dt
                } else {
                    b.vy = (b.vy + 650f * u * dt).coerceAtMost(240f * u)
                    b.v += b.vy * dt
                    b.r = (b.r * (1f - 0.05f * dt)).coerceAtLeast(0.55f * u)
                    // Real drops stop and go as they run.
                    if (Random.nextFloat() < dt * 1.4f) {
                        b.pause = 0.2f + Random.nextFloat() * 0.5f
                        b.vy = 0f
                    }
                }
                if (b.v > sf.rect.height + b.r) {
                    // It ran off the bottom edge and falls away.
                    drip(sf.rect.left + b.u, sf.rect.bottom, b.r)
                    beads.removeAt(i)
                    continue
                }
            }
            i++
        }
    }

    private fun drip(x: Float, y: Float, r: Float) {
        if (sparks.size > 220) return
        val sp = Spark()
        sp.x = x
        sp.y = y
        sp.vx = 0f
        sp.vy = 40f
        sp.maxLife = 0.5f
        sp.life = sp.maxLife
        sp.r = r * 0.8f
        sparks.add(sp)
    }

    private fun stepSparks(dt: Float, u: Float) {
        var i = 0
        while (i < sparks.size) {
            val s = sparks[i]
            s.life -= dt
            if (s.life <= 0f) {
                sparks.removeAt(i)
                continue
            }
            s.vy += 1100f * u * dt
            s.x += s.vx * dt
            s.y += s.vy * dt
            i++
        }
    }

    // ---- Drawing ----

    fun draw(scope: DrawScope, u: Float) {
        with(scope) {
            for (s in streaks) {
                val tx = s.x - LEAN * s.len
                val ty = s.y - s.len
                val mx = (s.x + tx) / 2f
                val my = (s.y + ty) / 2f
                // The tail is fainter than the head, so the streak looks soft.
                drawLine(Color.White.copy(alpha = s.alpha * 0.35f), Offset(tx, ty), Offset(mx, my), s.width, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = s.alpha), Offset(mx, my), Offset(s.x, s.y), s.width, StrokeCap.Round)
            }
            for (sp in sparks) {
                drawCircle(Color.White.copy(alpha = 0.75f * sp.life / sp.maxLife), sp.r, Offset(sp.x, sp.y))
            }
            for (b in beads) {
                val sf = byId[b.surface] ?: continue
                val cx = sf.rect.left + b.u
                val cy = sf.rect.top + b.v
                if (b.sliding && b.v > b.trailFrom) {
                    // The wet line a running drop leaves behind.
                    drawLine(
                        Color.White.copy(alpha = 0.10f),
                        Offset(cx, sf.rect.top + b.trailFrom), Offset(cx, cy),
                        b.r * 0.7f, StrokeCap.Round,
                    )
                }
                val c = Offset(cx, cy)
                drawCircle(Color.White.copy(alpha = 0.10f), b.r, c)
                drawCircle(Color.White.copy(alpha = 0.30f), b.r, c, style = Stroke(width = max(1f, u * 0.35f)))
                // Light bends through the drop: a bright dot at the top left, a faint glint at the bottom.
                drawCircle(Color.White.copy(alpha = 0.85f), b.r * 0.28f, Offset(cx - b.r * 0.3f, cy - b.r * 0.35f))
                drawCircle(Color.White.copy(alpha = 0.35f), b.r * 0.2f, Offset(cx + b.r * 0.25f, cy + b.r * 0.4f))
            }
        }
    }
}

/**
 * Rain on glass: soft falling streaks that hit the top of every surface in [surfaces] (the dock, or
 * the app tiles) with a little splash. Drops stick to the surface, grow when more land next to them,
 * run down in stop-and-go lines, and fall off the bottom edge.
 *
 * [surfaces] is asked once per frame, in pixels, so it can follow things that move.
 */
@Composable
fun RainGlass(modifier: Modifier = Modifier, surfaces: (Density) -> List<RainSurface>) {
    val density = LocalDensity.current
    val sim = remember { RainSim() }
    val surfacesNow by rememberUpdatedState(surfaces)
    var tick by remember { mutableLongStateOf(0L) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && size.width > 0) {
                    sim.step(
                        (now - last) / 1_000_000_000f,
                        size.width.toFloat(), size.height.toFloat(), density.density,
                        surfacesNow(density),
                    )
                }
                last = now
                tick = now
            }
        }
    }

    Canvas(modifier.onSizeChanged { size = it }) {
        tick // read, so the canvas is drawn again after every step
        sim.draw(this, density.density)
    }
}
