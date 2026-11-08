package com.fares.glass.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import java.util.Random
import java.util.TimeZone

/** Cozy living room with a window on the left that shows sunrise, day, sunset or night. */
object SkyWallpaper {

    enum class Phase { Sunrise, Day, Sunset, Night }

    private const val W = 1280
    private const val H = 720
    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    /**
     * Which look to show now. The sunrise and sunset times are moved to today, so old times
     * left over from yesterday (for example after the TV was in standby) still give the right answer.
     */
    fun phase(now: Long, sunrise: Long, sunset: Long): Phase {
        val midnight = localMidnight(now)
        val rise = midnight + timeOfDay(sunrise)
        val set = midnight + timeOfDay(sunset)
        return when {
            now < rise - 30 * MIN -> Phase.Night
            now < rise + 150 * MIN -> Phase.Sunrise
            now < set - 90 * MIN -> Phase.Day
            now < set + 45 * MIN -> Phase.Sunset
            else -> Phase.Night
        }
    }

    private fun localMidnight(t: Long): Long {
        val offset = tz.getOffset(t)
        return (t + offset) / DAY * DAY - offset
    }

    private fun timeOfDay(t: Long): Long = (t + tz.getOffset(t)) % DAY

    fun render(phase: Phase, rain: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Scene(Canvas(bmp), phase, rain).draw()
        return bmp
    }

    private class Scene(private val c: Canvas, private val phase: Phase, private val rain: Boolean) {

        private val w = W.toFloat()
        private val h = H.toFloat()
        private val clamp = Shader.TileMode.CLAMP
        private val rnd = Random(11)

        private val tvOn = phase == Phase.Night || phase == Phase.Sunset
        private val lamp = when (phase) {
            Phase.Day -> 0f
            Phase.Sunrise -> 0.35f
            Phase.Sunset -> 0.7f
            Phase.Night -> 1f
        }

        // Layout
        private val floorTop = h * 0.745f
        private val win = RectF(w * 0.05f, h * 0.22f, w * 0.285f, h * 0.68f)
        private val tvW = w * 0.40f
        private val tvH = tvW * 9f / 16f
        private val tvX = w * 0.40f
        private val tvY = h * 0.23f
        private val screen = RectF(tvX, tvY, tvX + tvW, tvY + tvH)

        // Colour of the light in the room: it tints everything that is not a light source.
        private val tintColor = if (rain) rgb(0x607080) else when (phase) {
            Phase.Sunrise -> rgb(0xFFB07A)
            Phase.Day -> rgb(0xFFFFFF)
            Phase.Sunset -> rgb(0xE0704A)
            Phase.Night -> rgb(0x0A1030)
        }
        private val tintBase = when (phase) {
            Phase.Sunrise -> 0.20f
            Phase.Day -> 0.04f
            Phase.Sunset -> 0.28f
            Phase.Night -> 0.62f
        }
        private val tintAlpha = if (rain && phase != Phase.Night) tintBase + 0.12f else tintBase

        // ---- colour helpers ----

        private fun rgb(v: Int): Int = v or (0xFF shl 24)

        private fun al(color: Int, f: Float): Int =
            Color.argb((f * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun mix(a: Int, b: Int, f: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * f).toInt().coerceIn(0, 255)
            return Color.argb(
                Color.alpha(a),
                ch(Color.red(a), Color.red(b)),
                ch(Color.green(a), Color.green(b)),
                ch(Color.blue(a), Color.blue(b)),
            )
        }

        /** A colour as it looks in this room's light. */
        private fun t(color: Int): Int = mix(color, tintColor, tintAlpha)

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

        private fun shade(s: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = s }

        private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color
        }

        fun draw() {
            wall()
            floor()
            rug()
            tvSet()
            floorLamp()
            plant(w * 0.975f, h * 0.80f, 1.3f)
            window()
            curtains()
            armchair()
            glows()
            vignette()
        }

        // ---- room ----

        private fun wall() {
            c.drawRect(0f, 0f, w, h, shade(LinearGradient(0f, 0f, 0f, h * 0.75f, t(rgb(0xD6BDA6)), t(rgb(0xB99E86)), clamp)))
            val stripe = fill(al(t(rgb(0xFFFFFF)), 0.04f))
            var x = 0f
            while (x < w) {
                c.drawRect(x, 0f, x + 26f, floorTop, stripe)
                x += 52f
            }
        }

        private fun floor() {
            c.drawRect(0f, floorTop, w, h, shade(LinearGradient(0f, floorTop, 0f, h, t(rgb(0x8A6240)), t(rgb(0x5C3E29)), clamp)))
            val line = stroke(al(t(rgb(0x2E1E12)), 0.30f), 2f)
            for (k in 1..5) {
                val y = floorTop + (h - floorTop) * (k / 6f)
                c.drawLine(0f, y, w, y, line)
            }
            for (k in 0..5) {
                val y0 = floorTop + (h - floorTop) * (k / 6f)
                val y1 = floorTop + (h - floorTop) * ((k + 1) / 6f)
                var x = if (k % 2 == 0) 0f else 110f
                while (x < w) {
                    c.drawLine(x, y0, x, y1, line)
                    x += 220f
                }
            }
            c.drawRect(0f, floorTop - 16f, w, floorTop, fill(t(rgb(0xEADFD0))))
            c.drawRect(0f, floorTop, w, floorTop + 5f, fill(al(rgb(0x000000), 0.25f)))
        }

        private fun rug() {
            val r = RectF(w * 0.30f, h * 0.80f, w * 0.96f, h * 1.02f)
            c.drawOval(r, fill(t(rgb(0x9C5446))))
            val inner = RectF(r.left + 24f, r.top + 12f, r.right - 24f, r.bottom - 12f)
            c.drawOval(inner, stroke(t(rgb(0xF0DCC0)), 4f))
        }

        private fun tvSet() {
            val top = h * 0.668f
            val x0 = w * 0.34f
            val x1 = w * 0.86f
            val bottom = h * 0.80f

            // console
            c.drawRect(x0 + 20f, bottom, x0 + 34f, bottom + 18f, fill(t(rgb(0x4B3425))))
            c.drawRect(x1 - 34f, bottom, x1 - 20f, bottom + 18f, fill(t(rgb(0x4B3425))))
            c.drawRoundRect(RectF(x0, top, x1, bottom), 8f, 8f, fill(t(rgb(0x8A6240))))
            c.drawRoundRect(RectF(x0 - 8f, top - 10f, x1 + 8f, top + 4f), 6f, 6f, fill(t(rgb(0xB08558))))
            val mid = (x0 + x1) / 2f
            for (i in 0..1) {
                val l = if (i == 0) x0 + 14f else mid + 7f
                val r = if (i == 0) mid - 7f else x1 - 14f
                val dTop = top + 16f
                val dBottom = bottom - 14f
                c.drawRoundRect(RectF(l, dTop, r, dBottom), 6f, 6f, fill(al(t(rgb(0x000000)), 0.12f)))
                c.drawCircle((l + r) / 2f, (dTop + dBottom) / 2f, 5f, fill(t(rgb(0xE0C690))))
            }

            // things on the console: books and a small plant
            val bx = x0 + 26f
            val by = top - 10f
            c.drawRect(bx, by - 22f, bx + 70f, by, fill(t(rgb(0xB5654A))))
            c.drawRect(bx + 6f, by - 42f, bx + 64f, by - 22f, fill(t(rgb(0x3E6C73))))
            c.drawRect(bx + 2f, by - 60f, bx + 58f, by - 42f, fill(t(rgb(0xD9A441))))
            plant(x1 - 70f, by, 0.8f)

            // TV
            c.drawRect(tvX + tvW / 2f - 60f, tvY + tvH + 6f, tvX + tvW / 2f + 60f, top - 10f, fill(rgb(0x14141A)))
            c.drawRoundRect(RectF(tvX - 7f, tvY - 7f, tvX + tvW + 7f, tvY + tvH + 7f), 10f, 10f, fill(rgb(0x0C0C10)))
            c.drawRoundRect(
                screen, 4f, 4f,
                shade(LinearGradient(tvX, tvY, tvX + tvW, tvY + tvH, rgb(0x141A26), rgb(0x232C3E), clamp)),
            )
            val reflection = Path().apply {
                moveTo(tvX + tvW * 0.55f, tvY)
                lineTo(tvX + tvW * 0.85f, tvY)
                lineTo(tvX + tvW * 0.45f, tvY + tvH)
                lineTo(tvX + tvW * 0.15f, tvY + tvH)
                close()
            }
            c.save()
            c.clipRect(screen)
            c.drawPath(reflection, fill(al(rgb(0xFFFFFF), 0.07f)))
            c.restore()
        }

        private fun floorLamp() {
            val x = w * 0.915f
            val shadeTop = h * 0.30f
            val shadeBottom = h * 0.395f
            c.drawRect(x - 3f, shadeBottom, x + 3f, h * 0.80f, fill(t(rgb(0x3A2C22))))
            c.drawOval(RectF(x - 30f, h * 0.79f, x + 30f, h * 0.815f), fill(t(rgb(0x3A2C22))))
            val lampShade = Path().apply {
                moveTo(x - 30f, shadeTop)
                lineTo(x + 30f, shadeTop)
                lineTo(x + 46f, shadeBottom)
                lineTo(x - 46f, shadeBottom)
                close()
            }
            c.drawPath(lampShade, fill(mix(t(rgb(0xE9D6B0)), rgb(0xFFE6A8), lamp)))
        }

        private fun plant(x: Float, base: Float, s: Float) {
            val greens = intArrayOf(0x4E8A52, 0x5FA064, 0x3E7446)
            val angles = floatArrayOf(-50f, -25f, 0f, 25f, 50f, -75f, 75f)
            for ((i, angle) in angles.withIndex()) {
                c.save()
                c.rotate(angle, x, base - 30f * s)
                val len = (90f + (i % 3) * 18f) * s
                c.drawOval(
                    RectF(x - 11f * s, base - 30f * s - len, x + 11f * s, base - 30f * s),
                    fill(t(rgb(greens[i % 3]))),
                )
                c.restore()
            }
            val pot = Path().apply {
                moveTo(x - 24f * s, base - 34f * s)
                lineTo(x + 24f * s, base - 34f * s)
                lineTo(x + 18f * s, base)
                lineTo(x - 18f * s, base)
                close()
            }
            c.drawPath(pot, fill(t(rgb(0xC77B5A))))
            c.drawRect(x - 27f * s, base - 40f * s, x + 27f * s, base - 32f * s, fill(t(rgb(0xD98D6B))))
        }

        // ---- window ----

        private fun skyColors(): IntArray = if (rain) {
            when (phase) {
                Phase.Sunrise -> intArrayOf(rgb(0x6B6575), rgb(0x8E8590), rgb(0xB5A69C))
                Phase.Day -> intArrayOf(rgb(0x6E7B88), rgb(0x8C99A5), rgb(0xAAB5BE))
                Phase.Sunset -> intArrayOf(rgb(0x5B5368), rgb(0x84707A), rgb(0xA98A7E))
                Phase.Night -> intArrayOf(rgb(0x0E131F), rgb(0x192131), rgb(0x2A3444))
            }
        } else {
            when (phase) {
                Phase.Sunrise -> intArrayOf(rgb(0x4A4E8F), rgb(0xE58A8A), rgb(0xFFC27A))
                Phase.Day -> intArrayOf(rgb(0x4FA3E8), rgb(0x8CCBF2), rgb(0xCDEBFA))
                Phase.Sunset -> intArrayOf(rgb(0x3A2A6B), rgb(0xD9566E), rgb(0xFF9A4D))
                Phase.Night -> intArrayOf(rgb(0x070B24), rgb(0x121C45), rgb(0x253766))
            }
        }

        private fun sun(cx: Float, cy: Float, r: Float, core: Int, edge: Int) {
            c.drawCircle(cx, cy, r * 4.5f, shade(RadialGradient(cx, cy, r * 4.5f, al(edge, 0.55f), al(edge, 0f), clamp)))
            c.drawCircle(cx, cy, r, shade(RadialGradient(cx, cy, r, core, edge, clamp)))
        }

        private fun moon(cx: Float, cy: Float, r: Float) {
            c.drawCircle(cx, cy, r * 3.6f, shade(RadialGradient(cx, cy, r * 3.6f, al(rgb(0xBFD3FF), 0.35f), al(rgb(0xBFD3FF), 0f), clamp)))
            val crescent = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
            val bite = Path().apply { addCircle(cx + r * 0.55f, cy - r * 0.12f, r * 0.88f, Path.Direction.CW) }
            crescent.op(bite, Path.Op.DIFFERENCE)
            c.drawPath(crescent, fill(rgb(0xF6F1DC)))
        }

        private fun cloud(cx: Float, cy: Float, s: Float, color: Int) {
            val p = fill(color)
            c.drawOval(RectF(cx - 40f * s, cy - 10f * s, cx + 40f * s, cy + 14f * s), p)
            c.drawOval(RectF(cx - 22f * s, cy - 24f * s, cx + 14f * s, cy + 6f * s), p)
            c.drawOval(RectF(cx - 2f * s, cy - 18f * s, cx + 34f * s, cy + 8f * s), p)
        }

        private fun window() {
            val ww = win.width()
            val wh = win.height()
            c.save()
            c.clipRect(win)

            // sky
            c.drawRect(win, shade(LinearGradient(0f, win.top, 0f, win.bottom, skyColors(), floatArrayOf(0f, 0.55f, 1f), clamp)))

            // sun or moon
            val horizon = win.top + wh * 0.72f
            if (!rain) {
                when (phase) {
                    Phase.Sunrise -> sun(win.left + ww * 0.62f, horizon + wh * 0.01f, ww * 0.095f, rgb(0xFFE3A0), rgb(0xFFA24D))
                    Phase.Day -> sun(win.left + ww * 0.70f, win.top + wh * 0.22f, ww * 0.08f, rgb(0xFFF6C8), rgb(0xFFE08A))
                    Phase.Sunset -> sun(win.left + ww * 0.38f, horizon - wh * 0.05f, ww * 0.125f, rgb(0xFFD58A), rgb(0xFF6A3D))
                    Phase.Night -> {
                        val star = fill(rgb(0xFFFFFF))
                        repeat(26) {
                            val x = win.left + rnd.nextFloat() * ww
                            val y = win.top + rnd.nextFloat() * wh * 0.62f
                            star.alpha = 90 + rnd.nextInt(150)
                            c.drawCircle(x, y, 0.8f + rnd.nextFloat() * 1.4f, star)
                        }
                        moon(win.left + ww * 0.66f, win.top + wh * 0.26f, ww * 0.11f)
                    }
                }
            }

            // clouds
            val cloudColor = when {
                rain -> al(rgb(0x59636E), 0.95f)
                phase == Phase.Sunrise -> al(rgb(0xFFC9B0), 0.75f)
                phase == Phase.Day -> al(rgb(0xFFFFFF), 0.9f)
                phase == Phase.Sunset -> al(rgb(0xFF9070), 0.7f)
                else -> al(rgb(0x39467A), 0.55f)
            }
            cloud(win.left + ww * 0.25f, win.top + wh * 0.30f, 1.1f, cloudColor)
            cloud(win.left + ww * 0.75f, win.top + wh * 0.45f, 0.9f, cloudColor)
            cloud(win.left + ww * 0.45f, win.top + wh * 0.58f, 0.8f, cloudColor)
            if (rain) {
                cloud(win.left + ww * 0.60f, win.top + wh * 0.16f, 1.4f, cloudColor)
                cloud(win.left + ww * 0.15f, win.top + wh * 0.50f, 1.2f, cloudColor)
            }

            // hills
            var far = rgb(
                when (phase) {
                    Phase.Sunrise -> 0x6A4F7E
                    Phase.Day -> 0x7DB07F
                    Phase.Sunset -> 0x56335F
                    Phase.Night -> 0x0E1630
                },
            )
            var near = rgb(
                when (phase) {
                    Phase.Sunrise -> 0x3F3159
                    Phase.Day -> 0x4F8F61
                    Phase.Sunset -> 0x2D1A3A
                    Phase.Night -> 0x070C1B
                },
            )
            if (rain) {
                far = mix(far, rgb(0x4A5560), 0.4f)
                near = mix(near, rgb(0x3A444E), 0.4f)
            }
            val farHills = Path().apply {
                moveTo(win.left, win.top + wh * 0.74f)
                quadTo(win.left + ww * 0.3f, win.top + wh * 0.60f, win.left + ww * 0.6f, win.top + wh * 0.72f)
                quadTo(win.left + ww * 0.85f, win.top + wh * 0.80f, win.right, win.top + wh * 0.70f)
                lineTo(win.right, win.bottom)
                lineTo(win.left, win.bottom)
                close()
            }
            c.drawPath(farHills, fill(far))
            val nearHills = Path().apply {
                moveTo(win.left, win.top + wh * 0.86f)
                quadTo(win.left + ww * 0.35f, win.top + wh * 0.74f, win.left + ww * 0.7f, win.top + wh * 0.88f)
                quadTo(win.left + ww * 0.9f, win.top + wh * 0.94f, win.right, win.top + wh * 0.84f)
                lineTo(win.right, win.bottom)
                lineTo(win.left, win.bottom)
                close()
            }
            c.drawPath(nearHills, fill(near))
            if (phase == Phase.Night) {
                // a few lit windows in the distance
                val lit = fill(al(rgb(0xFFD27A), 0.9f))
                for (i in 0 until 7) {
                    c.drawCircle(win.left + ww * (0.12f + 0.12f * i), win.top + wh * (0.90f + 0.02f * (i % 3)), 1.6f, lit)
                }
            }

            // (The rain on the window is no longer drawn into this picture: the animated rain
            // that Glass draws on top of the screen covers the window too.)

            // reflection on the glass
            val streak = Path().apply {
                moveTo(win.left + ww * 0.15f, win.top)
                lineTo(win.left + ww * 0.35f, win.top)
                lineTo(win.left + ww * 0.05f, win.bottom)
                lineTo(win.left - ww * 0.15f, win.bottom)
                close()
            }
            c.drawPath(streak, fill(al(rgb(0xFFFFFF), 0.08f)))
            c.restore()

            // frame, bars and sill
            val frameColor = t(rgb(0xF2E9DC))
            c.drawRect(RectF(win.left - 7f, win.top - 7f, win.right + 7f, win.bottom + 7f), stroke(frameColor, 14f))
            val bar = fill(frameColor)
            c.drawRect(win.centerX() - 4f, win.top, win.centerX() + 4f, win.bottom, bar)
            c.drawRect(win.left, win.centerY() - 4f, win.right, win.centerY() + 4f, bar)
            c.drawRoundRect(RectF(win.left - 22f, win.bottom + 7f, win.right + 22f, win.bottom + 24f), 4f, 4f, fill(t(rgb(0xEADFCF))))
            c.drawRect(win.left - 22f, win.bottom + 24f, win.right + 22f, win.bottom + 28f, fill(al(rgb(0x000000), 0.18f)))
        }

        private fun curtains() {
            val rodY = win.top - 34f
            val rodColor = t(rgb(0x4A3526))
            c.drawLine(win.left - 70f, rodY, win.right + 70f, rodY, stroke(rodColor, 7f))
            c.drawCircle(win.left - 70f, rodY, 9f, fill(rodColor))
            c.drawCircle(win.right + 70f, rodY, 9f, fill(rodColor))
            curtain(win.left - 64f, win.left + 40f, rodY)
            curtain(win.right - 40f, win.right + 64f, rodY)
        }

        private fun curtain(x0: Float, x1: Float, y0: Float) {
            c.drawRect(x0, y0, x1, floorTop, fill(t(rgb(0xB8664C))))
            val fold = fill(al(t(rgb(0x5E2E20)), 0.28f))
            val step = (x1 - x0) / 6f
            for (i in 0 until 6 step 2) {
                c.drawRect(x0 + step * i, y0, x0 + step * (i + 1), floorTop, fold)
            }
        }

        private fun armchair() {
            val x0 = w * 0.045f
            val x1 = w * 0.245f
            val topY = h * 0.63f
            val botY = h * 0.805f
            val legs = fill(t(rgb(0x4A3526)))
            c.drawRect(x0 + 18f, botY - 4f, x0 + 30f, botY + 14f, legs)
            c.drawRect(x1 - 30f, botY - 4f, x1 - 18f, botY + 14f, legs)
            c.drawRoundRect(RectF(x0 + 20f, topY, x1 - 20f, botY - 40f), 36f, 36f, fill(t(rgb(0x6E8B74))))
            val arm = fill(t(rgb(0x58735E)))
            c.drawRoundRect(RectF(x0 - 6f, topY + 52f, x0 + 44f, botY), 24f, 24f, arm)
            c.drawRoundRect(RectF(x1 - 44f, topY + 52f, x1 + 6f, botY), 24f, 24f, arm)
            c.drawRoundRect(RectF(x0 + 30f, topY + 76f, x1 - 30f, botY - 4f), 18f, 18f, fill(t(rgb(0x7FA088))))
            c.save()
            c.rotate(-14f, x0 + 80f, topY + 90f)
            c.drawRoundRect(RectF(x0 + 52f, topY + 54f, x0 + 112f, topY + 112f), 14f, 14f, fill(t(rgb(0xE0A94B))))
            c.restore()
        }

        // ---- light ----

        private fun glows() {
            // light coming in through the window
            val spill = when (phase) {
                Phase.Sunrise -> rgb(0xFFB070)
                Phase.Day -> rgb(0xFFF1D0)
                Phase.Sunset -> rgb(0xFF8A50)
                Phase.Night -> rgb(0x7FA0FF)
            }
            val base = when (phase) {
                Phase.Sunrise -> 0.30f
                Phase.Day -> 0.22f
                Phase.Sunset -> 0.34f
                Phase.Night -> 0.14f
            }
            val strength = if (rain) base * 0.6f else base
            val beam = Path().apply {
                moveTo(win.left, win.bottom)
                lineTo(win.right, win.bottom)
                lineTo(win.right + 260f, h * 0.99f)
                lineTo(win.left + 140f, h * 0.99f)
                close()
            }
            c.drawPath(beam, shade(LinearGradient(0f, win.bottom, 0f, h, al(spill, strength), al(spill, 0f), clamp)))
            c.drawCircle(
                win.centerX(), win.centerY(), w * 0.30f,
                shade(RadialGradient(win.centerX(), win.centerY(), w * 0.30f, al(spill, strength * 0.6f), al(spill, 0f), clamp)),
            )

            // TV is on in the evening
            if (tvOn) {
                val cx = screen.centerX()
                val cy = screen.centerY()
                c.drawCircle(cx, cy, w * 0.36f, shade(RadialGradient(cx, cy, w * 0.36f, al(rgb(0x5C9CFF), 0.22f), al(rgb(0x5C9CFF), 0f), clamp)))
                c.save()
                c.clipRect(screen)
                c.drawRect(screen, shade(LinearGradient(tvX, tvY, tvX + tvW, tvY + tvH, rgb(0x1F3A6B), rgb(0x6B3FA0), clamp)))
                val gx = tvX + tvW * 0.30f
                val gy = tvY + tvH * 0.70f
                c.drawCircle(gx, gy, tvH * 0.8f, shade(RadialGradient(gx, gy, tvH * 0.8f, al(rgb(0x39D0C0), 0.55f), al(rgb(0x39D0C0), 0f), clamp)))
                val hx = tvX + tvW * 0.78f
                val hy = tvY + tvH * 0.28f
                c.drawCircle(hx, hy, tvH * 0.7f, shade(RadialGradient(hx, hy, tvH * 0.7f, al(rgb(0xFF6FA8), 0.45f), al(rgb(0xFF6FA8), 0f), clamp)))
                c.restore()
            }

            // floor lamp
            if (lamp > 0f) {
                val lx = w * 0.915f
                val ly = h * 0.35f
                c.drawCircle(lx, ly, w * 0.30f, shade(RadialGradient(lx, ly, w * 0.30f, al(rgb(0xFFC477), 0.38f * lamp), al(rgb(0xFFC477), 0f), clamp)))
            }
        }

        /** Darker corners and a darker top, so the clock and the verse stay easy to read. */
        private fun vignette() {
            c.drawRect(
                0f, 0f, w, h,
                shade(
                    RadialGradient(
                        w / 2f, h * 0.45f, w * 0.75f,
                        intArrayOf(0, 0, al(rgb(0x000000), 0.38f)),
                        floatArrayOf(0f, 0.55f, 1f),
                        clamp,
                    ),
                ),
            )
            c.drawRect(0f, 0f, w, h * 0.30f, shade(LinearGradient(0f, 0f, 0f, h * 0.30f, al(rgb(0x000000), 0.30f), al(rgb(0x000000), 0f), clamp)))
        }
    }
}
