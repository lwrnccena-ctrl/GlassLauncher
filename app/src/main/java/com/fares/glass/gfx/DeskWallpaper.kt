// DeskWallpaper.kt
//
// Android Canvas version of the desk-room wallpaper (same scene as DeskWallpaper_render.html).
//
// USAGE:
//   val bmp: Bitmap = DeskWallpaper.render(DeskWallpaper.Phase.Night, DeskWallpaper.Weather.Rain)
//   imageView.setImageBitmap(bmp)
//
// Output is always 1280x720. Draw it in a background thread and cache the Bitmap
// (it takes a moment to draw). Scale it with ImageView.scaleType = CENTER_CROP
// or ContentScale.Crop in Compose.

package com.fares.glass.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object DeskWallpaper {

    enum class Phase { Day, Sunrise, Sunset, Night }
    enum class Weather { Sunny, Clear, Foggy, Cloudy, Drizzle, Rain, Storm }

    const val WIDTH = 1280
    const val HEIGHT = 720

    fun render(phase: Phase, weather: Weather, pics: DeskPictures? = null): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        Scene(bmp, phase, weather, pics).draw()
        return bmp
    }

    /**
     * Drop-in for SkyWallpaper.render(phase, rain): same inputs, so the existing
     * time (SkyWallpaper.phase) and rain logic keeps working unchanged.
     */
    fun render(phase: SkyWallpaper.Phase, rain: Boolean, pics: DeskPictures? = null): Bitmap {
        val p = when (phase) {
            SkyWallpaper.Phase.Sunrise -> Phase.Sunrise
            SkyWallpaper.Phase.Day -> Phase.Day
            SkyWallpaper.Phase.Sunset -> Phase.Sunset
            SkyWallpaper.Phase.Night -> Phase.Night
        }
        return render(p, if (rain) Weather.Rain else Weather.Sunny, pics)
    }

    private class Scene(
        bitmap: Bitmap,
        private val phase: Phase,
        private val weather: Weather,
        private val pics: DeskPictures?,
    ) {

        // A "paint spec": either a fill (color or gradient) or a round-capped stroke.
        private class Pt(val color: Int, val shader: Shader?, val stroke: Boolean, val width: Float)

        private val w = WIDTH.toDouble()
        private val h = HEIGHT.toDouble()
        private var c = Canvas(bitmap)
        private val tmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rnd = Random(21)

        private val dim = when (weather) {
            Weather.Sunny, Weather.Clear -> 0.0
            Weather.Foggy -> 0.30
            Weather.Cloudy -> 0.50
            Weather.Drizzle -> 0.60
            Weather.Rain -> 0.75
            Weather.Storm -> 0.95
        }
        private val daylight = when (phase) {
            Phase.Day -> 1.0
            Phase.Sunrise -> 0.55
            Phase.Sunset -> 0.45
            Phase.Night -> 0.0
        }
        private val night = phase == Phase.Night
        private val raining = weather == Weather.Drizzle || weather == Weather.Rain || weather == Weather.Storm

        // perspective
        private val vx = w * 0.72
        private val vy = h * 0.48
        private val farScale = 0.42
        private val m = h / 3
        private val eye = (h - vy) / m
        private val bx0 = px(0, 1)
        private val bx1 = px(w, 1)
        private val by0 = py(0, 1)
        private val by1 = py(h, 1)
        private val hy = vy

        private val deskTop = 0.75
        private val deskT = 0.04
        private val dd = 0.80
        private val dz1 = 0.03
        private val dz2 = 0.88

        private val ledCol = rgb(0xFFB259)
        private val ledK = 0.75 + 0.25 * (1 - daylight)

        private val tintColor = mix(
            byPhase(sunrise = rgb(0xFFB07A), day = rgb(0xFFFFFF), sunset = rgb(0xE0704A), night = rgb(0x0A1030)),
            rgb(0x607080), dim * 0.6
        )
        private val tintAlpha = min(
            0.7,
            byPhase(sunrise = 0.14, day = 0.03, sunset = 0.18, night = 0.50) + dim * 0.10
        )

        fun draw() {
            windowSky(); glass(); floor()
            reflect(0.66, 3.5, false); reflect(0.20, 1.75, true)
            leftWall(); pictureFrame(); ceiling(); shelves(); deskBody(); monitors(); deskItems(); chairs()
            spill(); particles(); vignette()
        }

        // ---------------------------------------------------------------- helpers

        private val Double.f: Float get() = this.toFloat()
        private fun Random.rf(): Double = nextFloat().toDouble()
        private fun d(n: Number): Double = n.toDouble()

        private fun <T> byPhase(sunrise: T, day: T, sunset: T, night: T): T = when (phase) {
            Phase.Sunrise -> sunrise
            Phase.Day -> day
            Phase.Sunset -> sunset
            Phase.Night -> night
        }

        private fun rgb(v: Int): Int = v or 0xFF000000.toInt()
        private fun al(x: Int, f: Number): Int =
            Color.argb((d(f) * 255).toInt().coerceIn(0, 255), Color.red(x), Color.green(x), Color.blue(x))

        private fun mix(a: Int, b: Int, f: Double): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * f).toInt().coerceIn(0, 255)
            return Color.argb(
                Color.alpha(a),
                ch(Color.red(a), Color.red(b)),
                ch(Color.green(a), Color.green(b)),
                ch(Color.blue(a), Color.blue(b))
            )
        }

        private fun t(x: Int): Int = mix(x, tintColor, tintAlpha)

        private fun F(x: Int) = Pt(x, null, false, 0f)
        private fun S(x: Int, wd: Number) = Pt(x, null, true, d(wd).f)

        private fun LG(x0: Number, y0: Number, x1: Number, y1: Number, c0: Int, c1: Int) = Pt(
            0, LinearGradient(d(x0).f, d(y0).f, d(x1).f, d(y1).f, c0, c1, Shader.TileMode.CLAMP), false, 0f
        )

        private fun LG(x0: Number, y0: Number, x1: Number, y1: Number, cols: IntArray, pos: FloatArray) = Pt(
            0, LinearGradient(d(x0).f, d(y0).f, d(x1).f, d(y1).f, cols, pos, Shader.TileMode.CLAMP), false, 0f
        )

        private fun RG(x: Number, y: Number, r: Number, c0: Int, c1: Int) = Pt(
            0, RadialGradient(d(x).f, d(y).f, max(d(r), 0.01).f, c0, c1, Shader.TileMode.CLAMP), false, 0f
        )

        private fun RG(x: Number, y: Number, r: Number, cols: IntArray, pos: FloatArray) = Pt(
            0, RadialGradient(d(x).f, d(y).f, max(d(r), 0.01).f, cols, pos, Shader.TileMode.CLAMP), false, 0f
        )

        private fun use(p: Pt): Paint {
            paint.reset()
            paint.isAntiAlias = true
            if (p.stroke) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = p.width
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = p.color
            } else {
                paint.style = Paint.Style.FILL
                if (p.shader != null) paint.shader = p.shader else paint.color = p.color
            }
            return paint
        }

        private fun rect(l: Number, top: Number, r: Number, b: Number, p: Pt) {
            val x0 = d(l); val y0 = d(top); val x1 = d(r); val y1 = d(b)
            c.drawRect(min(x0, x1).f, min(y0, y1).f, max(x0, x1).f, max(y0, y1).f, use(p))
        }

        private fun oval(l: Number, top: Number, r: Number, b: Number, p: Pt) {
            val x0 = d(l); val y0 = d(top); val x1 = d(r); val y1 = d(b)
            c.drawOval(RectF(min(x0, x1).f, min(y0, y1).f, max(x0, x1).f, max(y0, y1).f), use(p))
        }

        private fun circ(x: Number, y: Number, r: Number, p: Pt) {
            c.drawCircle(d(x).f, d(y).f, max(0.0, d(r)).f, use(p))
        }

        private fun line(x0: Number, y0: Number, x1: Number, y1: Number, p: Pt) {
            c.drawLine(d(x0).f, d(y0).f, d(x1).f, d(y1).f, use(p))
        }

        private fun rrect(l: Number, top: Number, r: Number, b: Number, k: Number, p: Pt) {
            val x0 = d(l); val y0 = d(top); val x1 = d(r); val y1 = d(b)
            c.drawRoundRect(RectF(min(x0, x1).f, min(y0, y1).f, max(x0, x1).f, max(y0, y1).f), d(k).f, d(k).f, use(p))
        }

        private fun dp(path: Path, p: Pt) {
            c.drawPath(path, use(p))
        }

        private fun poly(vararg v: Number): Path {
            val p = Path()
            p.moveTo(v[0].toFloat(), v[1].toFloat())
            var i = 2
            while (i < v.size) {
                p.lineTo(v[i].toFloat(), v[i + 1].toFloat()); i += 2
            }
            p.close()
            return p
        }

        private fun polyOf(pts: List<DoubleArray>): Path {
            val p = Path()
            pts.forEachIndexed { i, q -> if (i == 0) p.moveTo(q[0].f, q[1].f) else p.lineTo(q[0].f, q[1].f) }
            p.close()
            return p
        }

        // perspective helpers
        private fun sc(z: Number): Double = 1 - d(z) * (1 - farScale)
        private fun px(x: Number, z: Number): Double = vx + (d(x) - vx) * sc(z)
        private fun py(y: Number, z: Number): Double = vy + (d(y) - vy) * sc(z)
        private fun rx(xm: Number, z: Number): Double = px(d(xm) * m, z)
        private fun ry(hm: Number, z: Number): Double = py(h - d(hm) * m, z)

        // polygon in room coordinates: triples of (xMeters, heightMeters, depth)
        private fun room(vararg v: Number): Path {
            val p = Path()
            var i = 0
            while (i + 2 < v.size) {
                val x = rx(v[i], v[i + 2])
                val y = ry(v[i + 1], v[i + 2])
                if (i == 0) p.moveTo(x.f, y.f) else p.lineTo(x.f, y.f)
                i += 3
            }
            p.close()
            return p
        }

        private fun glow(x: Number, y: Number, r: Number, col: Int, a: Number) {
            circ(x, y, r, RG(x, y, r, al(col, a), al(col, 0)))
        }

        private fun led(x1: Number, y1: Number, x2: Number, y2: Number, wd: Double) {
            line(x1, y1, x2, y2, S(al(ledCol, 0.10 * ledK), wd * 8))
            line(x1, y1, x2, y2, S(al(ledCol, 0.24 * ledK), wd * 3.4))
            line(x1, y1, x2, y2, S(al(rgb(0xFFE6BC), 0.95), wd))
        }

        private fun floorPath(): Path = poly(0, h, bx0, by1, bx1, by1, w, h)

        // ---------------------------------------------------------------- sky

        private fun skyColors(): IntArray {
            val cr = byPhase(
                sunrise = intArrayOf(0x4A4E8F, 0xE58A8A, 0xFFC27A),
                day = intArrayOf(0x4A9BE6, 0x8CCBF2, 0xD6EEFB),
                sunset = intArrayOf(0x4A78C4, 0xA9B8DA, 0xF4A468),
                night = intArrayOf(0x070B24, 0x121C45, 0x253766)
            )
            val gr = byPhase(
                sunrise = intArrayOf(0x6B6575, 0x8E8590, 0xB5A69C),
                day = intArrayOf(0x6E7B88, 0x8C99A5, 0xAAB5BE),
                sunset = intArrayOf(0x5B5368, 0x84707A, 0xA98A7E),
                night = intArrayOf(0x0E131F, 0x192131, 0x2A3444)
            )
            return IntArray(3) { mix(rgb(cr[it]), rgb(gr[it]), dim * 0.9) }
        }

        private fun sun(cx: Double, cy: Double, r: Double, core: Int, edge: Int, a: Double) {
            glow(cx, cy, r * 6, edge, 0.55 * a)
            circ(cx, cy, r, RG(cx, cy, r, al(core, a), al(edge, a)))
        }

        private fun moon(cx: Double, cy: Double, r: Double, a: Double) {
            glow(cx, cy, r * 4, rgb(0xBFD3FF), 0.35 * a)
            c.save()
            val p = Path()
            p.fillType = Path.FillType.EVEN_ODD
            p.addRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Path.Direction.CW)
            p.addCircle((cx + r * 0.55).f, (cy - r * 0.12).f, (r * 0.88).f, Path.Direction.CW)
            c.clipPath(p)
            circ(cx, cy, r, F(al(rgb(0xF6F1DC), a)))
            c.restore()
        }

        private fun cloud(cx: Double, cy: Double, s: Double, color: Int, rim: Int) {
            if (rim != 0) oval(cx - 44 * s, cy - 2 * s, cx + 44 * s, cy + 18 * s, F(rim))
            val p = F(color)
            oval(cx - 46 * s, cy - 10 * s, cx + 46 * s, cy + 15 * s, p)
            oval(cx - 26 * s, cy - 27 * s, cx + 16 * s, cy + 7 * s, p)
            oval(cx - 2 * s, cy - 20 * s, cx + 38 * s, cy + 9 * s, p)
        }

        private fun skyline(base: Double, minH: Double, maxH: Double, color: Int, lit: Double) {
            var x = 0.0
            val wc = F(al(rgb(0xFFD27A), 0.85 * lit))
            val body = F(color)
            while (x < w) {
                val bw = 22 + rnd.rf() * 30
                val bh = minH + rnd.rf() * (maxH - minH)
                rect(x, base - bh, x + bw, h, body)
                if (lit > 0) {
                    for (j in 0 until 6) {
                        if (rnd.rf() < 0.35) {
                            val wx = x + 4 + (j % 3) * 7
                            val wy = base - bh + 8 + (j / 3) * 11
                            rect(wx, wy, wx + 3, wy + 4, wc)
                        }
                    }
                }
                x += bw + rnd.rf() * 6
            }
        }

        private fun landmark(x: Double, base: Double, color: Int, lit: Double) {
            val topY = hy - h * 0.17
            val p = Path()
            p.moveTo((x - 22).f, base.f)
            p.lineTo((x - 18).f, (topY + 110).f)
            p.lineTo((x - 7).f, (topY + 24).f)
            p.lineTo(x.f, topY.f)
            p.lineTo((x + 7).f, (topY + 24).f)
            p.lineTo((x + 18).f, (topY + 110).f)
            p.lineTo((x + 22).f, base.f)
            p.close()
            dp(p, F(color))
            dp(poly(x, topY, x + 7, topY + 24, x + 18, topY + 110, x + 22, base, x, base), F(al(rgb(0xFFFFFF), 0.07)))
            if (lit > 0) {
                val band = F(al(rgb(0xFFD27A), 0.7 * lit))
                for (i in 0 until 10) {
                    val y = topY + 50 + i * ((base - topY - 70) / 10)
                    rect(x - 9, y, x + 9, y + 2.5, band)
                }
            }
        }

        private fun windowSky() {
            val a = 1 - dim * 0.9
            c.save()
            rect(0, 0, w, h, LG(0, 0, 0, hy * 1.1, skyColors(), floatArrayOf(0f, 0.6f, 1f)))
            if (phase == Phase.Sunrise || phase == Phase.Sunset) {
                val gc = if (phase == Phase.Sunset) rgb(0xFF8A3D) else rgb(0xFFB070)
                val ga = if (phase == Phase.Sunset) 0.75 else 0.50
                rect(0, hy - h * 0.28, w, hy + 4, LG(0, hy - h * 0.28, 0, hy, al(gc, 0), al(gc, ga * a)))
            }
            if (night) {
                repeat(60) {
                    val x = rnd.rf() * w
                    val y = rnd.rf() * hy * 0.9
                    val a8 = ((60 + rnd.nextInt(170)) * (1 - dim)).toInt()
                    circ(x, y, 0.7 + rnd.rf() * 1.3, F(Color.argb(a8, 255, 255, 255)))
                }
                moon(w * 0.58, hy * 0.70, w * 0.022, a)
            } else {
                val gb = if (weather == Weather.Sunny) 1.0 else 0.8
                when (phase) {
                    Phase.Sunrise -> sun(w * 0.90, hy * 0.82, w * 0.026, rgb(0xFFE3A0), rgb(0xFFA24D), a * gb)
                    Phase.Day -> sun(w * 0.62, hy * 0.62, w * 0.022, rgb(0xFFF6C8), rgb(0xFFE08A), a * gb)
                    else -> sun(w * 0.62, hy * 1.0, w * 0.026, rgb(0xFFD58A), rgb(0xFF6A3D), a * gb)
                }
            }
            val base = byPhase(sunrise = rgb(0xF2B9AC), day = rgb(0xFFFFFF), sunset = rgb(0x2F3F6B), night = rgb(0x39467A))
            val gcld = if (night) rgb(0x232B3B) else rgb(0x5A6470)
            val cc = al(mix(base, gcld, dim), if (night) 0.6 else 0.92)
            val rim = if (!night && phase != Phase.Day) al(rgb(0xFFA060), 0.85 * (1 - dim)) else 0
            val spots = listOf(
                doubleArrayOf(0.45, 0.66, 1.2), doubleArrayOf(0.57, 0.80, 1.0), doubleArrayOf(0.70, 0.62, 1.4),
                doubleArrayOf(0.82, 0.74, 1.2), doubleArrayOf(0.93, 0.45, 1.3), doubleArrayOf(0.50, 0.90, 0.9),
                doubleArrayOf(0.76, 0.88, 1.1), doubleArrayOf(0.88, 0.84, 0.9), doubleArrayOf(0.62, 0.55, 0.8),
                doubleArrayOf(0.98, 0.65, 0.9)
            )
            val cnt = when (weather) {
                Weather.Sunny -> 6
                Weather.Clear -> 0
                Weather.Foggy -> 3
                Weather.Cloudy -> 7
                Weather.Drizzle -> 8
                Weather.Rain -> 9
                Weather.Storm -> 10
            }
            for (i in 0 until cnt) {
                val s = spots[i]
                cloud(w * s[0], hy * s[1], s[2] * 1.3, cc, rim)
            }
            val far = mix(
                byPhase(sunrise = rgb(0x8A6A86), day = rgb(0x93AEC6), sunset = rgb(0x7A86A6), night = rgb(0x151D3C)),
                rgb(0x4A5560), dim * 0.4
            )
            val mid = mix(
                byPhase(sunrise = rgb(0x5A4A6B), day = rgb(0x6E8AA6), sunset = rgb(0x4A5778), night = rgb(0x0A1026)),
                rgb(0x3A444E), dim * 0.4
            )
            val lit = byPhase(sunrise = 0.3, day = 0.0, sunset = 0.6, night = 1.0)
            skyline(hy + h * 0.045, h * 0.03, h * 0.12, far, lit * 0.5)
            landmark(w * 0.70, hy + h * 0.06, mix(far, rgb(0x2F5F7A), 0.30), lit * 0.6)
            skyline(hy + h * 0.13, h * 0.03, h * 0.10, mid, lit * 0.9)

            val treeDark = ((1 - daylight * (1 - dim * 0.4)) * 0.85).coerceIn(0.0, 1.0)
            val gt = hy + h * 0.17
            val n0 = rgb(0x0B1A12)
            rect(0, gt, w, h, F(mix(rgb(0x4E8A3C), n0, treeDark)))
            repeat(14) {
                val x0 = rnd.rf() * w
                val y0 = gt + rnd.rf() * (h - gt - 40)
                val rw = 50 + rnd.rf() * 50
                val rh = 22 + rnd.rf() * 22
                rect(x0, y0, x0 + rw, y0 + rh, F(mix(rgb(0xC9CDD2), n0, treeDark)))
                rect(x0, y0 + rh, x0 + rw, y0 + rh + 6, F(mix(rgb(0x8E949C), n0, treeDark)))
            }
            repeat(80) { i ->
                val tx = rnd.rf() * w
                val ty = gt + rnd.rf() * (h - gt)
                val r = 12 + rnd.rf() * 22
                val g = if (i % 2 == 0) rgb(0x4E8A3C) else rgb(0x3E7A34)
                circ(tx, ty, r, F(mix(g, n0, treeDark)))
            }
            if (lit > 0) {
                val lamp = F(al(rgb(0xFFD27A), 0.8 * lit))
                repeat(50) {
                    val x = rnd.rf() * w
                    val y = gt + rnd.rf() * (h - gt)
                    circ(x, y, 1.6, lamp)
                }
            }
            val fogA = if (weather == Weather.Foggy) 0.62 else dim * 0.14
            if (fogA > 0) {
                val fog = byPhase(sunrise = rgb(0xD8C0B8), day = rgb(0xCDD5DC), sunset = rgb(0xB89AA0), night = rgb(0x2A3444))
                rect(0, 0, w, h, LG(0, 0, 0, h, al(fog, fogA * 0.55), al(fog, fogA)))
                if (weather == Weather.Foggy) {
                    for (i in 0 until 3) {
                        val y = h * (0.40 + 0.15 * i)
                        val x0 = w * (0.45 + 0.20 * i)
                        oval(x0 - 240, y - 30, x0 + 360, y + 30, F(al(fog, 0.30)))
                    }
                }
            }
            // (The falling rain streaks are no longer drawn into this picture: the animated rain
            // that Glass draws on top of the screen covers the whole wallpaper.)
            if (weather == Weather.Storm) {
                var boltX = w * 0.62
                var boltY = by0 - 10
                val bolt = Path()
                bolt.moveTo(boltX.f, boltY.f)
                for (o in listOf(14, -18, 20, -12, 16, -20, 12, -16)) {
                    boltY += h * 0.06
                    boltX += o
                    bolt.lineTo(boltX.f, boltY.f)
                }
                dp(bolt, S(al(rgb(0xBFD3FF), 0.35), 9))
                dp(bolt, S(rgb(0xFFFFFF), 2.6))
                rect(0, 0, w, h, F(al(rgb(0xDCE6FF), 0.07)))
            }
            if (raining) {
                val dr = F(al(rgb(0xFFFFFF), 0.18))
                repeat(if (weather == Weather.Drizzle) 20 else 40) {
                    val x = bx0 + rnd.rf() * (w - bx0)
                    val y = rnd.rf() * h
                    oval(x - 2, y - 3.5, x + 2, y + 3.5, dr)
                }
            }
            c.restore()
        }

        // ---------------------------------------------------------------- room

        private fun glass() {
            val fp = F(t(rgb(0xE8E5DF)))
            val edge = F(al(rgb(0), 0.20))
            val bw = bx1 - bx0
            dp(poly(bx0 + bw * 0.10, by0, bx0 + bw * 0.28, by0, bx0 + bw * 0.14, by1, bx0, by1), F(al(rgb(0xFFFFFF), 0.06)))
            dp(poly(w * 0.93, 0, w, 0, w, h * 0.5, w * 0.96, h * 0.5), F(al(rgb(0xFFFFFF), 0.05)))
            val ly = by0 + (by1 - by0) * 0.10
            line(bx0 + bw * 0.30, ly, w, ly, S(al(ledCol, 0.18 * ledK), 7))
            line(bx0 + bw * 0.30, ly, w, ly, S(al(rgb(0xFFE6BC), 0.75 * ledK), 2))
            val sy = by0 + (by1 - by0) * 0.32
            dp(poly(bx0 + bw * 0.42, by0, bx0 + bw * 0.47, by0, bx0 + bw * 0.62, sy, bx0 + bw * 0.58, sy), F(al(rgb(0xFFFFFF), 0.07)))
            for (f in listOf(0.36, 0.70)) {
                val x = bx0 + bw * f
                rect(x - 4, by0, x + 4, by1, fp)
                rect(x + 2, by0, x + 4, by1, edge)
            }
            rect(bx1 - 6, by0, bx1 + 6, by1, fp)
            rect(bx1 + 3, by0, bx1 + 6, by1, edge)
            for (z in listOf(0.28, 0.58)) {
                val x = px(w, z)
                val bwd = 14 * sc(z)
                rect(x - bwd / 2, py(0, z), x + bwd / 2, py(h, z), fp)
                rect(x + bwd / 2 - 2, py(0, z), x + bwd / 2, py(h, z), edge)
            }
            val cw = bw * 0.14
            val wh = t(rgb(0xFFFFFF))
            rect(bx0, by0, bx0 + cw, by1, LG(bx0, 0, bx0 + cw, 0, al(wh, 0.42), al(wh, 0)))
            for (i in 1..4) {
                val x = bx0 + cw * i / 5
                line(x, by0, x, by1, S(al(wh, 0.10), 2))
            }
        }

        private fun floor() {
            val fp = floorPath()
            dp(fp, LG(0, by1, 0, h, t(rgb(0xE9E3D9)), t(rgb(0xF8F4ED))))
            c.save()
            c.clipPath(fp)
            val joint = S(al(rgb(0x6A5A48), 0.10), 1.4)
            val hi = S(al(rgb(0xFFFFFF), 0.35), 1)
            for (i in -8..16) {
                val xx = i * w / 8
                line(xx, h, px(xx, 1), by1, joint)
            }
            for (z in listOf(0.12, 0.30, 0.46, 0.60, 0.72, 0.82, 0.90, 0.96)) {
                val y = ry(0, z)
                line(0, y, w, y, joint)
                line(0, y + 1.5, w, y + 1.5, hi)
            }
            val sr = skyColors()[2]
            rect(0, by1, w, by1 + h * 0.30, LG(0, by1, 0, by1 + h * 0.30, al(sr, 0.50 * (1 - dim * 0.5)), al(sr, 0)))
            dp(poly(w * 0.62, by1, w * 0.80, by1, w * 0.72, h, w * 0.40, h), F(al(rgb(0xFFFFFF), 0.10)))
            dp(poly(w * 0.84, by1, w * 0.90, by1, w * 0.98, h, w * 0.86, h), F(al(rgb(0xFFFFFF), 0.07)))
            glow(w * 0.04, h * 0.96, w * 0.30, ledCol, 0.14 * ledK)
            val k = daylight * (1 - dim)
            if (k > 0) {
                val col = byPhase(sunrise = rgb(0xFFB070), day = rgb(0xFFF1D0), sunset = rgb(0xFF8A50), night = rgb(0xFFFFFF))
                dp(
                    room(2.6, 0, 0.97, 5.4, 0, 0.97, 5.4, 0, 0.25, 1.6, 0, 0.25),
                    LG(0, ry(0, 0.97), 0, ry(0, 0.25), al(col, 0.38 * k), al(col, 0.10 * k))
                )
            }
            dp(room(4.35, 0, 0.50, 5.35, 0, 0.50, 5.35, 0, 0.80, 4.35, 0, 0.80), F(t(rgb(0x14161B))))
            c.restore()
            val rl = t(rgb(0x4A4E55))
            line(bx0, by1 - 3, bx1, by1 - 3, S(rl, 6))
            line(bx1, by1 - 3, w, h - 3, S(rl, 6))
        }

        private fun chairSide(cx: Double, fy: Double, k: Double, accent: Int, redSeat: Boolean) {
            // racing-style chair seen from behind-right: u = forward/back, v = near/far, g = height
            fun P(u: Double, v: Double, g: Double) = doubleArrayOf(cx + (u + 0.42 * v) * k, fy - (g + 0.16 * v) * k)
            fun pg(pts: List<DoubleArray>, p: Pt) = dp(polyOf(pts), p)
            fun ln(a: DoubleArray, b: DoubleArray, p: Pt) = line(a[0], a[1], b[0], b[1], p)

            val white = t(rgb(0xF4F4F6))
            val soft = t(rgb(0xCFD3DA))
            val shd = t(rgb(0xA9AFBA))
            val dark = t(rgb(0x15181D))
            val grey = t(rgb(0x3A3F48))
            val acc = t(accent)
            val hub = P(0.0, 0.0, 0.17)
            val ends = (0 until 5).map { i ->
                val a = (i * 72 + 18) * Math.PI / 180
                doubleArrayOf(0.48 * cos(a), 0.48 * sin(a))
            }
            val far = ends.filter { it[1] >= 0 }
            val near = ends.filter { it[1] < 0 }
            fun leg(e: DoubleArray) {
                val p = P(e[0], e[1], 0.06)
                line(hub[0], hub[1], p[0], p[1], S(dark, 0.055 * k))
            }
            fun cast(e: DoubleArray) {
                val p = P(e[0], e[1], 0.05)
                circ(p[0], p[1], 0.052 * k, F(dark))
                circ(p[0], p[1], 0.052 * k, S(acc, 0.02 * k))
                circ(p[0], p[1], 0.02 * k, F(grey))
            }
            fun arm(v: Double) {
                val a = P(0.0, v, 0.52)
                val b = P(0.0, v, 0.82)
                line(a[0], a[1], b[0], b[1], S(grey, 0.045 * k))
                pg(listOf(P(-0.34, v - 0.05, 0.84), P(0.12, v - 0.05, 0.84), P(0.12, v + 0.05, 0.84), P(-0.34, v + 0.05, 0.84)), F(dark))
                pg(listOf(P(-0.34, v - 0.05, 0.84), P(0.12, v - 0.05, 0.84), P(0.12, v - 0.05, 0.80), P(-0.34, v - 0.05, 0.80)), F(grey))
            }
            fun ub(g: Double) = 0.22 + (g - 0.5) * 0.2

            // far side first
            arm(0.30)
            far.forEach { leg(it) }
            far.forEach { cast(it) }
            // gas cylinder
            val c0 = P(0.0, 0.0, 0.17)
            val c1 = P(0.0, 0.0, 0.47)
            line(c0[0], c0[1], c1[0], c1[1], S(grey, 0.06 * k))
            line(c0[0] - 0.015 * k, c0[1], c1[0] - 0.015 * k, c1[1], S(al(rgb(0xFFFFFF), 0.25), 0.012 * k))
            near.forEach { leg(it) }
            near.forEach { cast(it) }
            // headrest pillow peeking over the shell
            val hp = 0.14
            pg(listOf(P(ub(1.2) - hp, -0.11, 1.20), P(ub(1.2) - 0.02, -0.11, 1.20), P(ub(1.5) - 0.02, 0.11, 1.50), P(ub(1.5) - hp, 0.11, 1.50)), F(acc))
            // seat
            pg(listOf(P(-0.38, -0.27, 0.46), P(0.30, -0.27, 0.46), P(0.30, 0.27, 0.46), P(-0.38, 0.27, 0.46)), F(dark))
            pg(listOf(P(0.30, -0.27, 0.46), P(0.30, 0.27, 0.46), P(0.30, 0.27, 0.57), P(0.30, -0.27, 0.57)), F(shd))
            pg(listOf(P(-0.38, -0.27, 0.46), P(0.30, -0.27, 0.46), P(0.30, -0.27, 0.57), P(-0.38, -0.27, 0.57)), F(soft))
            ln(P(-0.38, -0.27, 0.55), P(0.30, -0.27, 0.55), S(acc, 0.016 * k))
            pg(listOf(P(-0.38, -0.27, 0.57), P(0.30, -0.27, 0.57), P(0.30, 0.27, 0.57), P(-0.38, 0.27, 0.57)), F(if (redSeat) acc else white))
            if (redSeat) {
                for (s in listOf(-1.0, 1.0)) {
                    pg(listOf(P(-0.38, s * 0.27, 0.57), P(0.30, s * 0.27, 0.57), P(0.30, s * 0.17, 0.57), P(-0.38, s * 0.17, 0.57)), F(white))
                }
            } else {
                ln(P(-0.38, -0.10, 0.57), P(0.30, -0.10, 0.57), S(al(rgb(0), 0.12), 0.01 * k))
                ln(P(-0.38, 0.10, 0.57), P(0.30, 0.10, 0.57), S(al(rgb(0), 0.12), 0.01 * k))
            }
            // backrest: black side, then white shell
            pg(listOf(P(ub(0.5) - 0.12, -0.27, 0.5), P(ub(0.5), -0.27, 0.5), P(ub(1.4), -0.27, 1.4), P(ub(1.4) - 0.12, -0.27, 1.4)), F(dark))
            ln(P(ub(0.5), -0.27, 0.5), P(ub(1.4), -0.27, 1.4), S(acc, 0.02 * k))
            val q = listOf(P(ub(0.5), -0.27, 0.5), P(ub(0.5), 0.27, 0.5), P(ub(1.4), 0.27, 1.4), P(ub(1.4), -0.27, 1.4))
            pg(q, LG(q[0][0], 0, q[1][0], 0, white, soft))
            pg(q, S(dark, 0.02 * k))
            pg(listOf(P(ub(0.62), -0.18, 0.62), P(ub(0.62), 0.18, 0.62), P(ub(1.25), 0.18, 1.25), P(ub(1.25), -0.18, 1.25)), S(al(rgb(0), 0.16), 0.012 * k))
            pg(listOf(P(ub(0.62), -0.035, 0.62), P(ub(0.62), 0.035, 0.62), P(ub(1.22), 0.035, 1.22), P(ub(1.22), -0.035, 1.22)), F(dark))
            for (v in listOf(-0.07, 0.07)) {
                ln(P(ub(0.62), v, 0.62), P(ub(1.22), v, 1.22), S(acc, 0.016 * k))
            }
            pg(listOf(P(ub(1.26), -0.12, 1.26), P(ub(1.26), 0.12, 1.26), P(ub(1.32), 0.12, 1.32), P(ub(1.32), -0.12, 1.32)), F(acc))
            arm(-0.30)
        }

        private fun reflect(z: Double, xm: Double, redSeat: Boolean) {
            val cx = rx(xm, z)
            val fy = ry(0, z)
            val k = m * sc(z)
            c.save()
            c.clipPath(floorPath())
            val main = c
            tmp.eraseColor(Color.TRANSPARENT)
            c = Canvas(tmp)
            c.save()
            c.translate(0f, (2 * fy).f)
            c.scale(1f, -1f)
            chairSide(cx, fy, k, rgb(0xE0451F), redSeat)
            c.restore()
            c = main
            val bp = Paint()
            bp.alpha = 95
            c.drawBitmap(tmp, 0f, 0f, bp)
            val fl = t(rgb(0xF3EEE6))
            rect(0, fy, w, fy + 1.5 * k, LG(0, fy, 0, fy + 1.5 * k, al(fl, 0), al(fl, 0.75)))
            c.restore()
        }

        private fun leftWall() {
            val p = poly(0, 0, bx0, by0, bx0, by1, 0, h)
            // black wall, a little lighter near the window so its shape still reads
            dp(p, LG(0, 0, bx0, 0, t(rgb(0x1A1B20)), t(rgb(0x0A0B0E))))
            c.save()
            c.clipPath(p)
            // slat panel (PVC wood look): every other slat a touch lighter, a dark groove and a thin highlight between them
            val slats = 40
            val lighter = F(al(rgb(0xFFFFFF), 0.04))
            for (i in 0 until slats step 2) {
                val za = i / slats.toDouble()
                val zb = (i + 1) / slats.toDouble()
                dp(room(0, 3, za, 0, 3, zb, 0, 0, zb, 0, 0, za), lighter)
            }
            val groove = S(al(rgb(0), 0.85), 2)
            val shine = S(al(rgb(0xFFFFFF), 0.08), 1)
            for (i in 0..slats) {
                val z = i / slats.toDouble()
                line(rx(0, z), ry(3, z), rx(0, z), ry(0, z), groove)
                line(rx(0, z) + 2, ry(3, z), rx(0, z) + 2, ry(0, z), shine)
            }
            rect(0, 0, bx0, h * 0.30, LG(0, 0, 0, h * 0.30, al(ledCol, 0.30 * ledK), al(ledCol, 0)))
            c.restore()
            // dark skirting, and thin light lines on the corners so the shape of the room stays clear
            dp(room(0, 0.08, 0, 0, 0.08, 1, 0, 0, 1, 0, 0, 0), F(t(rgb(0x24262C))))
            val rim = S(al(rgb(0xFFFFFF), 0.30), 1.6)
            line(0, 0, bx0, by0, rim) // wall and ceiling
            line(bx0, by0, bx0, by1, rim) // wall and back corner
            line(rx(0, 0), ry(0.08, 0), rx(0, 1), ry(0.08, 1), S(al(rgb(0xFFFFFF), 0.20), 1.2)) // top of the skirting
            line(0, h, bx0, by1, S(al(rgb(0xFFFFFF), 0.22), 1.4)) // wall and floor
        }

        private fun ledRoom(x1: Double, z1: Double, x2: Double, z2: Double) {
            led(rx(x1, z1), ry(3, z1), rx(x2, z2), ry(3, z2), 2.6)
        }

        private fun ceiling() {
            val p = poly(0, 0, w, 0, bx1, by0, bx0, by0)
            dp(p, LG(0, 0, 0, by0, t(rgb(0x15161B)), t(rgb(0x0C0D10))))
            c.save()
            c.clipPath(p)
            // slats running from the front to the back of the room
            val slats = 40
            val step = 5.4 / slats
            val lighter = F(al(rgb(0xFFFFFF), 0.04))
            for (i in 0 until slats step 2) {
                dp(room(i * step, 3, 0, (i + 1) * step, 3, 0, (i + 1) * step, 3, 1, i * step, 3, 1), lighter)
            }
            val groove = S(al(rgb(0), 0.85), 2)
            val shine = S(al(rgb(0xFFFFFF), 0.07), 1)
            for (i in 0..slats) {
                val x = i * step
                line(rx(x, 0), ry(3, 0), rx(x, 1), ry(3, 1), groove)
                line(rx(x, 0) + 2, ry(3, 0), rx(x, 1) + 2, ry(3, 1), shine)
            }
            dp(room(0.45, 3, 0.12, 4.2, 3, 0.12, 4.2, 3, 0.80, 0.45, 3, 0.80), F(al(rgb(0xFFE6C0), 0.22)))
            ledRoom(0.45, 0.12, 4.2, 0.12); ledRoom(4.2, 0.12, 4.2, 0.80)
            ledRoom(4.2, 0.80, 0.45, 0.80); ledRoom(0.45, 0.80, 0.45, 0.12)
            ledRoom(0.10, 0.96, 5.33, 0.96); ledRoom(5.33, 0.0, 5.33, 0.96); ledRoom(0.10, 0.0, 0.10, 0.96)
            for (x in listOf(1.3, 2.5, 3.7)) {
                val cx = rx(x, 0.46)
                val cy = ry(3, 0.46)
                glow(cx, cy, 40, rgb(0xFFE2B0), 0.35)
                oval(cx - 9, cy - 3.5, cx + 9, cy + 3.5, F(rgb(0xFFF3DC)))
            }
            c.restore()
            // thin light lines on the ceiling's corners
            val rim = S(al(rgb(0xFFFFFF), 0.30), 1.6)
            line(bx0, by0, bx1, by0, rim)
            line(w, 0, bx1, by0, rim)
        }

        // ---------------------------------------------------------------- pictures

        /**
         * Draws [bmp] on a four-corner shape of the screen with perspective. [q] = x,y of the top-left,
         * top-right, bottom-right and bottom-left corners. The picture is cropped to [aspect] (width / height).
         */
        private fun drawOnQuad(bmp: Bitmap, q: FloatArray, clip: Path, aspect: Double) {
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            var sw = bw
            var sh = bw / aspect.toFloat()
            if (sh > bh) {
                sh = bh
                sw = bh * aspect.toFloat()
            }
            val sx = (bw - sw) / 2f
            val sy = (bh - sh) / 2f
            val src = floatArrayOf(sx, sy, sx + sw, sy, sx + sw, sy + sh, sx, sy + sh)
            val matrix = Matrix()
            if (!matrix.setPolyToPoly(src, 0, q, 0, 4)) return
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            c.save()
            c.clipPath(clip)
            c.concat(matrix)
            c.drawBitmap(bmp, 0f, 0f, p)
            c.restore()
        }

        /** A picture frame on the left wall, high up (the top left of the wallpaper). */
        private fun pictureFrame() {
            val z0 = 0.30
            val z1 = 0.62
            val top = 2.95
            val bot = 2.28
            // wooden frame, then a light mat, then the picture
            dp(room(0, top, z0, 0, top, z1, 0, bot, z1, 0, bot, z0), F(t(rgb(0x4A3526))))
            val fz = 0.012
            val fh = 0.03
            dp(
                room(0, top - fh, z0 + fz, 0, top - fh, z1 - fz, 0, bot + fh, z1 - fz, 0, bot + fh, z0 + fz),
                F(t(rgb(0xF4EFE6))),
            )
            val pz0 = z0 + 0.032
            val pz1 = z1 - 0.032
            val pt = top - 0.08
            val pb = bot + 0.08
            val area = room(0, pt, pz0, 0, pt, pz1, 0, pb, pz1, 0, pb, pz0)
            val pic = pics?.frame
            if (pic != null) {
                val q = floatArrayOf(
                    rx(0, pz0).f, ry(pt, pz0).f, rx(0, pz1).f, ry(pt, pz1).f,
                    rx(0, pz1).f, ry(pb, pz1).f, rx(0, pz0).f, ry(pb, pz0).f,
                )
                val bb = RectF()
                area.computeBounds(bb, true)
                drawOnQuad(pic, q, area, (bb.width() / bb.height()).toDouble())
            } else {
                // No picture yet: a soft sunset so the frame is not empty.
                dp(area, LG(0, ry(pt, 0.46), 0, ry(pb, 0.46), rgb(0x6C8FD8), rgb(0xF2B38A)))
            }
            // the room's light on the picture, and a thin edge
            dp(area, F(al(tintColor, tintAlpha * 0.7)))
            dp(area, S(al(rgb(0), 0.25), 1.5))
        }

        // ---------------------------------------------------------------- shelves

        private fun shelf(hs: Double, z1: Double, z2: Double) {
            val depth = 0.28
            val th = 0.045
            val zm = (z1 + z2) / 2
            dp(
                room(0, hs - th, z1, 0, hs - th, z2, 0, hs - 0.45, z2, 0, hs - 0.45, z1),
                LG(0, ry(hs - th, zm), 0, ry(hs - 0.45, zm), al(ledCol, 0.55 * ledK), al(ledCol, 0))
            )
            if (hs < eye) {
                dp(room(0, hs, z1, depth, hs, z1, depth, hs, z2, 0, hs, z2), F(t(rgb(0xF6F4F0))))
            } else {
                dp(
                    room(0, hs - th, z1, depth, hs - th, z1, depth, hs - th, z2, 0, hs - th, z2),
                    F(mix(t(rgb(0xDDD8D0)), ledCol, 0.40 * ledK))
                )
            }
            dp(room(depth, hs, z1, depth, hs, z2, depth, hs - th, z2, depth, hs - th, z1), F(t(rgb(0xE2DFD9))))
            dp(room(0, hs, z1, depth, hs, z1, depth, hs - th, z1, 0, hs - th, z1), F(t(rgb(0xEFEDE8))))
            led(rx(depth - 0.02, z1), ry(hs - th, z1), rx(depth - 0.02, z2), ry(hs - th, z2), 2.2)
        }

        private fun figure(z: Double, hs: Double, kind: Int, accent: Int) {
            val u = 0.34 * m * sc(z)
            val ox = rx(0.14, z)
            val oy = ry(hs, z)
            val body = F(t(rgb(0x1E2128)))
            val mid = F(t(rgb(0x3A3F4A)))
            val ac = F(t(accent))
            val midc = t(rgb(0x3A3F4A))
            when (kind % 3) {
                0 -> {
                    rect(ox - 0.22 * u, oy - 0.34 * u, ox - 0.04 * u, oy, body)
                    rect(ox + 0.04 * u, oy - 0.34 * u, ox + 0.22 * u, oy, body)
                    rrect(ox - 0.28 * u, oy - 0.72 * u, ox + 0.28 * u, oy - 0.32 * u, 3, mid)
                    rect(ox - 0.10 * u, oy - 0.60 * u, ox + 0.10 * u, oy - 0.46 * u, ac)
                    circ(ox - 0.36 * u, oy - 0.66 * u, 0.13 * u, body)
                    circ(ox + 0.36 * u, oy - 0.66 * u, 0.13 * u, body)
                    rrect(ox - 0.12 * u, oy - 0.92 * u, ox + 0.12 * u, oy - 0.72 * u, 3, body)
                    rect(ox - 0.07 * u, oy - 0.85 * u, ox + 0.07 * u, oy - 0.81 * u, ac)
                }
                1 -> {
                    dp(poly(ox - 0.34 * u, oy, ox - 0.12 * u, oy - 0.75 * u, ox + 0.12 * u, oy - 0.75 * u, ox + 0.34 * u, oy), ac)
                    dp(poly(ox - 0.12 * u, oy - 0.70 * u, ox + 0.12 * u, oy - 0.70 * u, ox + 0.08 * u, oy, ox - 0.08 * u, oy), body)
                    circ(ox, oy - 0.88 * u, 0.13 * u, mid)
                }
                else -> {
                    rect(ox - 0.26 * u, oy - 0.30 * u, ox - 0.06 * u, oy, body)
                    rect(ox + 0.06 * u, oy - 0.30 * u, ox + 0.26 * u, oy, body)
                    rrect(ox - 0.24 * u, oy - 0.70 * u, ox + 0.24 * u, oy - 0.28 * u, 3, mid)
                    circ(ox, oy - 0.80 * u, 0.12 * u, body)
                    line(ox + 0.20 * u, oy - 0.58 * u, ox + 0.66 * u, oy - 0.92 * u, S(midc, 0.12 * u))
                    circ(ox + 0.66 * u, oy - 0.92 * u, 0.07 * u, ac)
                }
            }
        }

        private fun plant(z: Double, hs: Double) {
            val u = m * sc(z)
            val ox = rx(0.14, z)
            val oy = ry(hs, z)
            val top = oy - 0.12 * u
            val gr = intArrayOf(0x3E7446, 0x4E8A52, 0x2F5F3A)
            listOf(-55, -30, -8, 12, 34, 58).forEachIndexed { i, ang ->
                c.save()
                c.rotate(ang.toFloat(), ox.f, top.f)
                val len = (0.30 + (i % 3) * 0.05) * u
                oval(ox - 0.04 * u, top - len, ox + 0.04 * u, top, F(t(rgb(gr[i % 3]))))
                c.restore()
            }
            dp(poly(ox - 0.08 * u, top, ox + 0.08 * u, top, ox + 0.06 * u, oy, ox - 0.06 * u, oy), F(t(rgb(0xF0EEEA))))
        }

        private fun shelves() {
            dp(room(0, 2.62, 0, 0, 2.62, 0.13, 0, 1.99, 0.13, 0, 1.99, 0), F(rgb(0x0A0B10)))
            dp(
                room(0, 2.59, 0.012, 0, 2.59, 0.12, 0, 2.02, 0.12, 0, 2.02, 0.012),
                LG(0, ry(2.6, 0.07), 0, ry(2, 0.07), rgb(0x3A1F7A), rgb(0xFF7A3D))
            )
            shelf(1.50, 0.10, 0.82)
            shelf(1.95, 0.14, 0.70)
            val acc = intArrayOf(0xE0451F, 0x39B8FF, 0xFFB259, 0xB05BFF)
            listOf(0.16, 0.27, 0.38, 0.49, 0.60).forEachIndexed { i, z -> figure(z, 1.50, i, rgb(acc[i % 4])) }
            plant(0.73, 1.50)
            listOf(0.22, 0.34, 0.47, 0.60).forEachIndexed { i, z -> figure(z, 1.95, i + 1, rgb(acc[(i + 2) % 4])) }
        }

        // ---------------------------------------------------------------- desk

        private fun deskBody() {
            dp(room(0, 0, dz1, dd + 0.2, 0, dz1, dd + 0.2, 0, dz2, 0, 0, dz2), F(al(rgb(0), 0.16)))
            dp(room(0, deskTop - deskT, dz1, 0, deskTop - deskT, dz2, 0, 0, dz2, 0, 0, dz1), F(al(rgb(0), 0.32)))
            dp(room(dd - 0.05, deskTop - deskT, dz2, dd, deskTop - deskT, dz2, dd, 0, dz2, dd - 0.05, 0, dz2), F(t(rgb(0xD9D6D0))))
            val xT = 0.50
            val za = 0.45
            val zb = 0.65
            val h0 = 0.05
            val h1 = 0.50
            val zc = (za + zb) / 2
            glow(rx(xT, zc), ry((h0 + h1) / 2, zc), m * 0.7, rgb(0x2E8CFF), 0.22 + 0.12 * (1 - daylight))
            val body = room(xT, h1, za, xT, h1, zb, xT, h0, zb, xT, h0, za)
            dp(body, F(t(rgb(0x0D0F14))))
            dp(body, S(al(rgb(0xFFFFFF), 0.10), 1.5))
            dp(
                room(xT, h1 - 0.03, za + 0.01, xT, h1 - 0.03, zb - 0.01, xT, h0 + 0.03, zb - 0.01, xT, h0 + 0.03, za + 0.01),
                LG(0, ry(h1, zc), 0, ry(h0, zc), rgb(0x123A7A), rgb(0x0A1230))
            )
            val rw = abs(rx(xT, zb) - rx(xT, za)) * 0.34
            val rh = rw * 0.85
            val fx = rx(xT, zc)
            for (i in 0 until 2) {
                val fy = ry(h0 + (h1 - h0) * (0.25 + 0.50 * i), zc)
                glow(fx, fy, rw * 2.2, rgb(0x39B8FF), 0.40)
                oval(fx - rw, fy - rh, fx + rw, fy + rh, S(rgb(0x39B8FF), 2.5))
                oval(fx - rw * 0.3, fy - rh * 0.3, fx + rw * 0.3, fy + rh * 0.3, F(rgb(0x14171F)))
                for (kk in 0 until 6) {
                    val ang = (kk * 60 + i * 17) * 0.0174533
                    line(
                        fx + cos(ang) * rw * 0.3, fy + sin(ang) * rh * 0.3,
                        fx + cos(ang) * rw * 0.85, fy + sin(ang) * rh * 0.85,
                        S(al(rgb(0xFFFFFF), 0.16), 1.6)
                    )
                }
            }
            line(rx(xT, za + 0.02), ry(h0 + 0.04, za + 0.02), rx(xT, zb - 0.02), ry(h0 + 0.04, zb - 0.02), S(rgb(0x39B8FF), 2))
            dp(
                room(0, deskTop, dz1, dd, deskTop, dz1, dd, deskTop, dz2, 0, deskTop, dz2),
                LG(0, ry(deskTop, dz2), 0, ry(deskTop, dz1), t(rgb(0xF1F0EC)), t(rgb(0xFBFAF7)))
            )
            dp(room(dd, deskTop, dz1, dd, deskTop, dz2, dd, deskTop - deskT, dz2, dd, deskTop - deskT, dz1), F(t(rgb(0xE8E6E1))))
            dp(room(0, deskTop, dz1, dd, deskTop, dz1, dd, deskTop - deskT, dz1, 0, deskTop - deskT, dz1), F(t(rgb(0xEDEBE6))))
            val ux = 0.70
            val ft = deskTop - deskT - 0.005
            val fb = 0.04
            dp(room(ux, ft, dz1, ux, ft, dz1 + 0.13, ux, fb, dz1 + 0.13, ux, fb, dz1), F(t(rgb(0xD6D3CD))))
            dp(room(0, ft, dz1, ux, ft, dz1, ux, fb, dz1, 0, fb, dz1), F(t(rgb(0xF4F2EE))))
            val n = 4
            val gap = (ft - fb) / n
            for (i in 0 until n) {
                val th = ft - gap * i
                val bh = th - gap
                line(rx(0, dz1), ry(bh, dz1), rx(ux, dz1), ry(bh, dz1), S(al(rgb(0), 0.18), 2))
                val hyy = ry(th - gap * 0.30, dz1)
                line(rx(ux * 0.30, dz1), hyy, rx(ux * 0.70, dz1), hyy, S(t(rgb(0x1B1D22)), 5))
            }
        }

        private fun monitor(za: Double, zb: Double, xa: Double, xb: Double, pal: IntArray, seed: Int, idx: Int) {
            val bot = 0.92
            val top = 1.40
            val zc = (za + zb) / 2
            val xc = (xa + xb) / 2
            val s = sc(zc)
            glow(rx(xc, zc), ry((top + bot) / 2, zc), m * 0.95 * s, pal[1], 0.10 + 0.16 * (1 - daylight))
            val nx = rx(xc, zc)
            val yBot = ry(bot, zc)
            val yDesk = ry(deskTop, zc)
            rect(nx - 5 * s, yBot, nx + 5 * s, yDesk, F(t(rgb(0x1B1E26))))
            oval(nx - 0.14 * m * s, yDesk - 4 * s, nx + 0.14 * m * s, yDesk + 5 * s, F(t(rgb(0x14161C))))
            val bez = 0.014
            val zm = 0.006
            val bezel = room(xa, top + bez, za - zm, xb, top + bez, zb + zm, xb, bot - bez, zb + zm, xa, bot - bez, za - zm)
            dp(bezel, F(rgb(0x0A0B10)))
            dp(bezel, S(al(rgb(0xFFFFFF), 0.10), 1.2))
            val scr = room(xa, top, za, xb, top, zb, xb, bot, zb, xa, bot, za)
            c.save()
            c.clipPath(scr)
            val bb = RectF()
            scr.computeBounds(bb, true)
            val sl = bb.left.toDouble()
            val st = bb.top.toDouble()
            val sr2 = bb.right.toDouble()
            val sb = bb.bottom.toDouble()
            val bw = sr2 - sl
            val bh = sb - st
            val pic = pics?.monitors?.getOrNull(idx)
            if (pic != null) {
                // Your own picture on the screen, in perspective.
                val q = floatArrayOf(
                    rx(xa, za).f, ry(top, za).f, rx(xb, zb).f, ry(top, zb).f,
                    rx(xb, zb).f, ry(bot, zb).f, rx(xa, za).f, ry(bot, za).f,
                )
                drawOnQuad(pic, q, scr, bw / bh)
            } else {
            rect(sl, st, sr2, sb, LG(sl, st, sr2, sb, pal, floatArrayOf(0f, 0.55f, 1f)))
            val sr = Random(seed.toLong())
            for (i in 0 until 7) {
                val gx = sl + sr.rf() * bw
                val gy = st + sr.rf() * bh
                val gr = bw * (0.15 + sr.rf() * 0.25)
                glow(gx, gy, gr, if (sr.nextBoolean()) rgb(0x4FD8FF) else rgb(0xFF7AC0), 0.45)
            }
            val sil = Path()
            sil.moveTo(sl.f, sb.f)
            var xx = sl
            while (xx <= sr2) {
                sil.lineTo(xx.f, (sb - bh * (0.10 + 0.22 * sr.rf())).f)
                xx += bw / 10
            }
            sil.lineTo(sr2.f, sb.f)
            sil.close()
            dp(sil, F(al(rgb(0x0A0C22), 0.75)))
            }
            val glare = 0.05 + 0.06 * daylight * (1 - dim)
            dp(poly(sl + bw * 0.55, st, sl + bw * 0.80, st, sl + bw * 0.45, sb, sl + bw * 0.20, sb), F(al(rgb(0xFFFFFF), glare)))
            c.restore()
        }

        private fun monitors() {
            monitor(0.10, 0.33, 0.27, 0.22, intArrayOf(rgb(0x2B1B6E), rgb(0x8A3FD0), rgb(0xFF8A4D)), 3, 0)
            monitor(0.34, 0.57, 0.22, 0.22, intArrayOf(rgb(0x0B2A6E), rgb(0x2D7BE0), rgb(0x7CF0E0)), 5, 1)
            monitor(0.58, 0.81, 0.22, 0.27, intArrayOf(rgb(0x3A0F5E), rgb(0xE0407A), rgb(0xFFB454)), 9, 2)
        }

        private fun speaker(xm: Double, z: Double) {
            val s = sc(z)
            val cx = rx(xm, z)
            val by = ry(deskTop, z)
            val bw = 0.075 * m * s
            val bh = 0.20 * m * s
            rrect(cx - bw, by - bh, cx + bw, by, 4 * s, F(t(rgb(0x14161C))))
            val wy = by - bh * 0.42
            oval(cx - bw * 0.75, wy - bw * 0.95, cx + bw * 0.75, wy + bw * 0.95, F(t(rgb(0xBFC4CC))))
            oval(cx - bw * 0.55, wy - bw * 0.70, cx + bw * 0.55, wy + bw * 0.70, F(t(rgb(0x0F1116))))
            oval(cx - bw * 0.20, wy - bw * 0.25, cx + bw * 0.20, wy + bw * 0.25, F(t(rgb(0x3A3F48))))
        }

        private fun deskItems() {
            val y = deskTop + 0.004
            dp(room(0.30, y, 0.30, 0.74, y, 0.30, 0.74, y, 0.68, 0.30, y, 0.68), F(t(rgb(0x14171D))))
            dp(room(0.34, y, 0.06, 0.72, y, 0.06, 0.72, y, 0.21, 0.34, y, 0.21), F(t(rgb(0x14171D))))
            val ky = y + 0.012
            glow(rx(0.50, 0.47), ry(ky, 0.47), m * 0.5 * sc(0.47), rgb(0xB05BFF), 0.28)
            dp(room(0.38, ky, 0.37, 0.62, ky, 0.37, 0.62, ky, 0.57, 0.38, ky, 0.57), F(t(rgb(0x0C0E12))))
            val rb = intArrayOf(0x39D0FF, 0xB05BFF, 0xFF6FA8, 0xFF9E3D)
            for (i in 0 until 5) {
                val xk = 0.40 + i * 0.045
                line(rx(xk, 0.385), ry(ky, 0.385), rx(xk, 0.555), ry(ky, 0.555), S(al(rgb(rb[i % 4]), 0.85), 1.8))
            }
            val m1x = rx(0.56, 0.63)
            val m1y = ry(y + 0.02, 0.63)
            oval(m1x - 7, m1y - 4, m1x + 7, m1y + 4, F(t(rgb(0x1E222A))))
            val m2x = rx(0.52, 0.16)
            val m2y = ry(y + 0.02, 0.16)
            oval(m2x - 8, m2y - 4.5, m2x + 8, m2y + 4.5, F(t(rgb(0x1E222A))))
            circ(m2x, m2y, 1.6, F(al(rgb(0xFF9E3D), 0.9)))
            speaker(0.56, 0.095)
            speaker(0.56, 0.84)
        }

        private fun chairs() {
            for ((z, xm, rs) in listOf(Triple(0.66, 3.5, false), Triple(0.20, 1.75, true))) {
                val cx = rx(xm, z)
                val fy = ry(0, z)
                val k = m * sc(z)
                oval(cx - 0.55 * k, fy - 0.04 * k, cx + 0.55 * k, fy + 0.07 * k, F(al(rgb(0), 0.22)))
                chairSide(cx, fy, k, rgb(0xE0451F), rs)
            }
        }

        // ---------------------------------------------------------------- finishing

        private fun spill() {
            val col = byPhase(sunrise = rgb(0xFFB070), day = rgb(0xFFF1D0), sunset = rgb(0xFF8A50), night = rgb(0x7FA0FF))
            val base = byPhase(sunrise = 0.20, day = 0.12, sunset = 0.24, night = 0.08)
            glow(w * 0.60, h * 0.45, w * 0.60, col, base * (1 - dim * 0.5))
        }

        private fun particles() {
            val main = if (night) rgb(0x9CC8FF) else rgb(0xFFEBC8)
            val k = 0.55 + 0.45 * (1 - daylight)
            repeat(36) {
                val x = rnd.rf() * w
                val y = rnd.rf() * h
                val r = 1 + rnd.rf() * 2
                val a = (0.20 + rnd.rf() * 0.50) * k
                glow(x, y, r * 5, main, a * 0.5)
                circ(x, y, r, F(al(main, a)))
            }
        }

        private fun vignette() {
            rect(
                0, 0, w, h,
                RG(w / 2, h * 0.45, w * 0.75, intArrayOf(0, 0, al(rgb(0), 0.28)), floatArrayOf(0f, 0.5f, 1f))
            )
        }
    }
}
