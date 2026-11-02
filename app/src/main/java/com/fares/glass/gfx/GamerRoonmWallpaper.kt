package com.fares.glass.gfx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Calendar
import java.util.Random
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.sin

/**
 * Gamer bedroom seen from the middle of the room: loft bed with the PC desk in the right corner,
 * TV and neon sign on the back wall, a window on the left wall that shows the real time of day and
 * the weather. Everything is drawn once into a Bitmap (no WebView, no animation), so it is light on a TV.
 *
 * Optional: put your own picture in app/src/main/assets/poster.jpg and it is shown in the big frame
 * on the right wall.
 */
object GamerRoomWallpaper {

    enum class Sky { Clear, Cloudy, Foggy, Rain, Storm }

    private const val W = 1280
    private const val H = 720
    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Manila")

    fun render(context: Context, sky: Sky, nowMs: Long = System.currentTimeMillis()): Bitmap {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(W / 640f, H / 360f) // everything below is drawn on a 640 x 360 grid
        Scene(context, c, hour, sky).draw()
        return bmp
    }

    // ---- sky colours by hour: top, horizon, how dark the room is ----
    private class Key(val h: Float, val top: Int, val bot: Int, val dark: Float)

    private val keys = listOf(
        Key(0f, 0x0A1030, 0x1A2750, .62f), Key(5f, 0x1B2350, 0x3A3A6A, .55f),
        Key(6f, 0x5A6AA8, 0xFF9A6A, .30f), Key(7.5f, 0x5B9AE0, 0xFFD9A8, .12f),
        Key(10f, 0x4A90E2, 0xBFE0FF, .05f), Key(14f, 0x3C86E0, 0xB8DCFF, .03f),
        Key(17f, 0x5A8AD0, 0xFFC58A, .12f), Key(18.5f, 0x7A5AA0, 0xFF8A50, .30f),
        Key(19.5f, 0x2A2A64, 0xC05A50, .50f), Key(21f, 0x0C1236, 0x1A2750, .62f),
        Key(24f, 0x0A1030, 0x1A2750, .62f),
    )

    private class Wx(val grey: Float, val dim: Float, val sun: Float, val cloud: Float, val fog: Float, val rain: Boolean)

    private fun wx(s: Sky) = when (s) {
        Sky.Clear -> Wx(0f, 1f, 1f, .25f, 0f, false)
        Sky.Cloudy -> Wx(.45f, .85f, .3f, 1f, 0f, false)
        Sky.Foggy -> Wx(.6f, .9f, .2f, .5f, .75f, false)
        Sky.Rain -> Wx(.6f, .7f, .05f, 1f, .2f, true)
        Sky.Storm -> Wx(.75f, .5f, 0f, 1f, .2f, true)
    }

    private class Scene(private val context: Context, private val c: Canvas, hour: Float, sky: Sky) {

        private val clamp = Shader.TileMode.CLAMP
        private val w = wx(sky)
        private val hr = hour

        // ---- light ----
        private val seg: Int = run { var i = 0; while (i < keys.size - 2 && hr >= keys[i + 1].h) i++; i }
        private val ka = keys[seg]
        private val kb = keys[seg + 1]
        private val f = (hr - ka.h) / (kb.h - ka.h)
        private val raw = ka.dark + (kb.dark - ka.dark) * f
        private val grey = mix(rgb(0x9AA0AB), rgb(0x1A1E2B), raw)
        private val top = mix(mix(mix(rgb(ka.top), rgb(kb.top), f), grey, w.grey), rgb(0x10131C), (1f - w.dim) * .8f)
        private val bot = mix(mix(mix(rgb(ka.bot), rgb(kb.bot), f), grey, w.grey * .9f), rgb(0x10131C), (1f - w.dim) * .7f)
        private val dk = 1f - (1f - raw) * w.dim
        private val night = ((raw - .3f) / .3f).coerceIn(0f, 1f)
        private val day = hr >= 6f && hr < 18.5f
        private val moonUp = hr >= 19f || hr < 5.5f
        private val p = when {
            day -> (hr - 6f) / 12.5f
            moonUp -> if (hr >= 19f) (hr - 19f) / 10.5f else (hr + 5f) / 10.5f
            else -> 0f
        }
        private val sx = 28f + p * 76f
        private val sy = 172f - sin(PI * p.toDouble()).toFloat() * 74f
        private val clock = "%02d:%02d".format(hr.toInt(), ((hr - hr.toInt()) * 60f).toInt())

        // ---- helpers ----
        private fun rgb(v: Int): Int = v or (0xFF shl 24)
        private fun al(color: Int, a: Float): Int =
            Color.argb((a * 255).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
            return Color.argb(255, ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
        }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private fun shade(s: Shader) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = s }
        private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width; this.color = color; strokeCap = Paint.Cap.ROUND
        }

        private fun lin(x0: Float, y0: Float, x1: Float, y1: Float, a: Int, b: Int) = LinearGradient(x0, y0, x1, y1, a, b, clamp)
        private fun lin3(x0: Float, y0: Float, x1: Float, y1: Float, a: Int, b: Int, d: Int) =
            LinearGradient(x0, y0, x1, y1, intArrayOf(a, b, d), floatArrayOf(0f, .5f, 1f), clamp)

        private fun poly(vararg v: Float): Path {
            val path = Path()
            path.moveTo(v[0], v[1])
            var i = 2
            while (i < v.size) { path.lineTo(v[i], v[i + 1]); i += 2 }
            path.close()
            return path
        }

        private fun rr(l: Float, t: Float, wd: Float, ht: Float, r: Float, paint: Paint) =
            c.drawRoundRect(RectF(l, t, l + wd, t + ht), r, r, paint)

        private fun metal(l: Float, wd: Float) =
            shade(LinearGradient(l, 0f, l + wd, 0f, intArrayOf(rgb(0x0B0C11), rgb(0x3D4152), rgb(0x0B0C11)), floatArrayOf(0f, .45f, 1f), clamp))

        private fun rgbBar(l: Float, wd: Float) =
            shade(LinearGradient(l, 0f, l + wd, 0f, intArrayOf(rgb(0xFF2BD6), rgb(0x6A7AFF), rgb(0x22D3EE), rgb(0x7CFF6A)), null, clamp))

        /** Soft round light (or shadow) spot. */
        private fun glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, a: Float) {
            c.save(); c.translate(cx, cy); c.scale(rx, ry)
            c.drawCircle(0f, 0f, 1f, shade(RadialGradient(0f, 0f, 1f, al(color, a.coerceIn(0f, 1f)), al(color, 0f), clamp)))
            c.restore()
        }

        private fun contactShadow(cx: Float, cy: Float, rx: Float, ry: Float) = glow(cx, cy, rx, ry, rgb(0x000000), .6f)

        private val poster: Bitmap? by lazy {
            runCatching { context.assets.open("poster.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }

        fun draw() {
            room(); posterFrame(); shelf(); rug(); beanbag(); tvUnit()
            c.save(); c.translate(-80f, 0f); furniture(); c.restore()
            // the room gets darker at night / in bad weather
            c.drawRect(0f, 0f, 640f, 360f, fill(al(rgb(0x070A1C), dk * .9f)))
            window(); curtain()
            lightsRoom()
            c.save(); c.translate(-80f, 0f); lightsDesk(); c.restore()
            c.drawRect(0f, 0f, 640f, 360f, shade(RadialGradient(320f, 180f, 400f, intArrayOf(0, 0, al(rgb(0), .55f)), floatArrayOf(0f, .55f, 1f), clamp)))
        }

        // ---- room shell ----
        private fun room() {
            c.drawPath(poly(0f, 0f, 640f, 0f, 510f, 60f, 130f, 60f), fill(rgb(0x1B1E2E)))
            c.drawRect(130f, 60f, 510f, 270f, shade(lin3(130f, 0f, 510f, 0f, rgb(0x2A2F44), rgb(0x3A405C), rgb(0x2A2F44))))
            c.drawPath(poly(0f, -12f, 130f, 60f, 130f, 270f, 0f, 342f), shade(lin(0f, 0f, 130f, 0f, rgb(0x4A5070), rgb(0x2A2F44))))
            c.drawPath(poly(640f, -12f, 510f, 60f, 510f, 270f, 640f, 342f), shade(lin(510f, 0f, 640f, 0f, rgb(0x2A2F44), rgb(0x4A5070))))
            val floor = poly(0f, 342f, 130f, 270f, 510f, 270f, 640f, 342f, 640f, 360f, 0f, 360f)
            c.drawPath(floor, shade(LinearGradient(0f, 270f, 0f, 360f, intArrayOf(rgb(0x3A281B), rgb(0x5A3D2A), rgb(0x43301F)), floatArrayOf(0f, .5f, 1f), clamp)))
            c.save(); c.clipPath(floor)
            val line = stroke(al(rgb(0x1F140C), .55f), 1f)
            var x = 0f
            while (x <= 640f) { c.drawLine(x, 360f, 320f, 165f, line); x += 64f }
            c.drawLine(0f, 330f, 320f, 165f, line); c.drawLine(640f, 330f, 320f, 165f, line)
            val seam = stroke(al(rgb(0x1F140C), .3f), 1f)
            for (y in floatArrayOf(280f, 292f, 308f, 330f, 354f)) c.drawLine(0f, y, 640f, y, seam)
            c.restore()
            c.drawPath(poly(0f, 342f, 130f, 270f, 130f, 262f, 0f, 327f), fill(rgb(0xD8D9E2)))
            c.drawRect(130f, 262f, 510f, 270f, fill(rgb(0xC9CAD6)))
            c.drawPath(poly(640f, 342f, 510f, 270f, 510f, 262f, 640f, 327f), fill(rgb(0xA9ABBB)))
        }

        /** The big poster frame on the right wall: your own picture if poster.jpg exists, else a placeholder. */
        private fun posterFrame() {
            c.drawPath(poly(520f, 117f, 608f, 97f, 608f, 182f, 520f, 177f), fill(rgb(0x0B0B10)))
            val dst = floatArrayOf(524f, 119f, 604f, 100f, 604f, 179f, 524f, 175f)
            val img = poster
            if (img != null) {
                val iw = img.width.toFloat(); val ih = img.height.toFloat()
                val m = Matrix()
                m.setPolyToPoly(floatArrayOf(0f, 0f, iw, 0f, iw, ih, 0f, ih), 0, dst, 0, 4)
                c.drawBitmap(img, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            } else {
                val box = poly(*dst)
                c.save(); c.clipPath(box)
                c.drawRect(520f, 90f, 610f, 190f, shade(lin(0f, 100f, 0f, 180f, rgb(0x2F6FD0), rgb(0xA9CDFA))))
                c.drawPath(poly(528f, 178f, 556f, 98f, 564f, 98f, 536f, 178f), fill(al(rgb(0x22D3EE), .85f)))
                c.drawPath(poly(556f, 180f, 586f, 96f, 590f, 96f, 562f, 180f), fill(al(rgb(0x7AE8FF), .7f)))
                c.drawPath(poly(572f, 172f, 606f, 146f, 606f, 153f, 578f, 178f), fill(al(rgb(0xFF2BD6), .8f)))
                c.restore()
            }
        }

        private fun shelf() {
            c.drawPath(poly(540f, 214f, 594f, 225f, 594f, 229f, 540f, 218f), fill(rgb(0xD8D9E2)))
            rr(548f, 200f, 6f, 15f, 2f, fill(rgb(0xFF4466))); rr(556f, 203f, 5f, 13f, 2f, fill(rgb(0x22D3EE)))
            rr(563f, 201f, 6f, 15f, 2f, fill(rgb(0xFFC23A))); rr(572f, 205f, 5f, 10f, 2f, fill(rgb(0xE8B830)))
            c.drawCircle(584f, 208f, 4f, fill(rgb(0xF2D0B0))); rr(580f, 212f, 8f, 10f, 3f, fill(rgb(0xFF2BD6)))
            contactShadow(568f, 228f, 30f, 4f)
        }

        private fun rug() {
            c.drawPath(poly(250f, 292f, 490f, 292f, 540f, 334f, 200f, 334f), fill(rgb(0x1D1A33)))
        }

        private fun beanbag() {
            contactShadow(76f, 338f, 64f, 9f)
            c.drawOval(RectF(22f, 300f, 122f, 344f), shade(RadialGradient(55f, 314f, 62f, rgb(0xC2305A), rgb(0x4A0F24), clamp)))
            c.drawOval(RectF(38f, 306f, 78f, 318f), fill(al(rgb(0xFFFFFF), .12f)))
        }

        private fun tvUnit() {
            contactShadow(176f, 232f, 50f, 5f)
            rr(138f, 214f, 76f, 14f, 3f, fill(rgb(0x1B1D26))); rr(138f, 214f, 76f, 3f, 1.5f, fill(rgb(0x2A2D3A)))
            rr(144f, 228f, 4f, 8f, 2f, fill(rgb(0x12131A))); rr(204f, 228f, 4f, 8f, 2f, fill(rgb(0x12131A)))
            rr(170f, 206f, 8f, 8f, 2f, fill(rgb(0x12131A)))
            rr(142f, 160f, 68f, 40f, 2.5f, fill(rgb(0x07070B)))
            rr(190f, 206f, 18f, 8f, 2.5f, fill(rgb(0x0D0D12)))
        }

        // ---- loft bed + desk (drawn shifted left by 80 so it sits in the right corner) ----
        private fun furniture() {
            contactShadow(454f, 304f, 46f, 8f); contactShadow(455f, 292f, 140f, 8f)
            val dark = fill(rgb(0x0F1016))
            rr(306f, 60f, 7f, 208f, 0f, dark); rr(557f, 60f, 7f, 208f, 0f, dark)
            rr(312f, 108f, 250f, 22f, 10f, shade(lin(0f, 108f, 0f, 130f, rgb(0xE9E9F0), rgb(0xA9ABBB))))
            rr(384f, 100f, 178f, 30f, 14f, shade(lin(0f, 100f, 0f, 130f, rgb(0x7452B8), rgb(0x3A2468))))
            c.drawOval(RectF(320f, 97f, 364f, 115f), shade(RadialGradient(336f, 102f, 30f, rgb(0xD2D6EA), rgb(0x7F84A4), clamp)))
            rr(500f, 104f, 32f, 15f, 7f, fill(rgb(0x16161D)))
            c.drawCircle(508f, 111f, 3f, fill(rgb(0x2A2C36))); c.drawCircle(524f, 111f, 3f, fill(rgb(0x2A2C36)))
            rr(300f, 130f, 280f, 12f, 4f, metal(300f, 280f)); rr(306f, 142f, 262f, 4f, 2f, fill(rgb(0x0A0A0F)))
            rr(300f, 56f, 9f, 234f, 4f, metal(300f, 9f)); rr(571f, 56f, 9f, 234f, 4f, metal(571f, 9f))
            rr(345f, 80f, 235f, 5f, 2.5f, fill(rgb(0x20222C))); rr(345f, 98f, 235f, 4f, 2f, fill(rgb(0x20222C)))
            rr(306f, 144f, 4f, 146f, 2f, fill(rgb(0x20222C))); rr(332f, 144f, 4f, 146f, 2f, fill(rgb(0x20222C)))
            for (y in floatArrayOf(166f, 192f, 218f, 244f, 270f)) rr(308f, y, 26f, 4f, 2f, fill(rgb(0x2A2D3A)))
            // headset hanging on the ladder
            val arc = Path().apply { addArc(RectF(312f, 157f, 330f, 175f), 180f, 180f) }
            c.drawPath(arc, stroke(rgb(0x15151C), 3f))
            rr(309f, 164f, 5f, 12f, 2.5f, fill(rgb(0x15151C))); rr(328f, 164f, 5f, 12f, 2.5f, fill(rgb(0x15151C)))
            // desk
            c.drawPath(poly(342f, 226f, 574f, 226f, 566f, 216f, 350f, 216f), shade(lin(0f, 216f, 0f, 226f, rgb(0x3A3D4A), rgb(0x1B1D26))))
            rr(342f, 226f, 232f, 5f, 2f, fill(rgb(0x151720)))
            rr(346f, 231f, 6f, 59f, 3f, metal(346f, 6f)); rr(564f, 231f, 6f, 59f, 3f, metal(564f, 6f)); rr(352f, 262f, 212f, 3f, 1.5f, fill(rgb(0x12131A)))
            // monitors
            c.drawOval(RectF(426f, 219f, 478f, 225f), fill(rgb(0x0B0B10))); rr(448f, 202f, 8f, 18f, 3f, metal(448f, 8f))
            rr(392f, 150f, 120f, 52f, 3f, fill(rgb(0x07070B)))
            c.drawOval(RectF(352f, 218.4f, 380f, 223.6f), fill(rgb(0x0B0B10))); rr(363f, 212f, 6f, 9f, 2f, metal(363f, 6f))
            rr(350f, 148f, 32f, 64f, 3f, fill(rgb(0x07070B)))
            // PC tower
            rr(524f, 168f, 44f, 54f, 4f, shade(lin3(524f, 0f, 568f, 0f, rgb(0x101016), rgb(0x262731), rgb(0x101016))))
            rr(527f, 171f, 38f, 48f, 2.5f, fill(rgb(0x06070B)))
            // mouse mat, keyboard, mouse, cans
            c.drawPath(poly(390f, 225f, 500f, 225f, 497f, 216f, 393f, 216f), fill(rgb(0x0D0E14)))
            c.drawPath(poly(396f, 222f, 466f, 222f, 463f, 217f, 399f, 217f), fill(rgb(0x12131A)))
            c.drawOval(RectF(477f, 217.4f, 487f, 222.6f), fill(rgb(0x1A1B24)))
            rr(505f, 204f, 8f, 17f, 3f, fill(rgb(0x22D3EE))); rr(514f, 206f, 8f, 15f, 3f, fill(rgb(0xFF2BD6)))
            // gaming chair (seen from behind)
            rr(418f, 224f, 64f, 62f, 22f, shade(lin3(418f, 0f, 482f, 0f, rgb(0x101016), rgb(0x262731), rgb(0x101016))))
            rr(436f, 212f, 28f, 18f, 8f, fill(rgb(0xA8182C)))
            rr(426f, 238f, 9f, 40f, 4.5f, fill(rgb(0xA8182C))); rr(465f, 238f, 9f, 40f, 4.5f, fill(rgb(0xA8182C)))
            rr(441f, 238f, 18f, 40f, 6f, fill(rgb(0x0B0B10)))
            rr(408f, 262f, 9f, 12f, 4f, fill(rgb(0x14141A))); rr(483f, 262f, 9f, 12f, 4f, fill(rgb(0x14141A)))
            rr(446f, 286f, 8f, 12f, 3f, metal(446f, 8f)); rr(418f, 298f, 64f, 5f, 2.5f, metal(418f, 64f))
            for (cx in floatArrayOf(420f, 450f, 480f)) c.drawCircle(cx, 306f, 3.5f, fill(rgb(0x0B0B10)))
        }

        // ---- window on the left wall ----
        private fun window() {
            val win = poly(6f, 40f, 118f, 85f, 118f, 196f, 6f, 214f)
            c.save(); c.clipPath(win)
            c.drawRect(0f, 40f, 130f, 214f, shade(lin(0f, 40f, 0f, 214f, top, bot)))
            val rnd = Random(11)
            val starA = night * (1f - w.cloud * .9f) * (1f - w.fog)
            if (starA > .02f) {
                val star = fill(Color.WHITE)
                repeat(14) { star.alpha = (starA * 255).toInt(); c.drawCircle(12f + rnd.nextFloat() * 100f, 56f + rnd.nextFloat() * 56f, if (it % 3 == 0) 1.3f else .8f, star) }
            }
            val sunCol = mix(rgb(0xFFF6D0), mix(rgb(kb.bot), rgb(ka.bot), 1f - f), .35f)
            if (day) {
                glow(sx, sy, 36f, 36f, sunCol, (.15f + w.sun * .85f) * .9f)
                c.drawCircle(sx, sy, 8f, fill(al(sunCol, maxOf(w.sun, .08f))))
            } else if (moonUp) {
                val a = maxOf(w.sun, .06f)
                c.drawCircle(sx, sy, 7f, fill(al(rgb(0xF2F4FF), a)))
                c.drawCircle(sx - 2.5f, sy - 2f, 1.6f, fill(al(rgb(0xD3D8EE), a))); c.drawCircle(sx + 2.5f, sy + 2.5f, 1.2f, fill(al(rgb(0xD3D8EE), a)))
            }
            var cc = mix(mix(rgb(0xFFFFFF), bot, .3f), rgb(0x2A2F3C), ((1f - w.dim) * 1.3f).coerceAtMost(1f))
            cc = mix(cc, rgb(0x0A1030), raw * .7f)
            val cloud = fill(al(cc, w.cloud * .92f))
            for (e in arrayOf(floatArrayOf(34f, 76f, 22f, 6f), floatArrayOf(48f, 70f, 13f, 6f), floatArrayOf(88f, 104f, 24f, 7f),
                floatArrayOf(78f, 97f, 13f, 6f), floatArrayOf(28f, 112f, 17f, 5f), floatArrayOf(102f, 94f, 12f, 5f))) {
                c.drawOval(RectF(e[0] - e[2], e[1] - e[3], e[0] + e[2], e[1] + e[3]), cloud)
            }
            val bld = fill(mix(rgb(0x1C2030), rgb(0x05070F), raw))
            val lit = fill(rgb(0xFFD9A0))
            var j = 0
            for (b in arrayOf(floatArrayOf(6f, 14f, 150f), floatArrayOf(18f, 12f, 136f), floatArrayOf(28f, 16f, 160f), floatArrayOf(42f, 12f, 128f),
                floatArrayOf(52f, 16f, 148f), floatArrayOf(66f, 12f, 138f), floatArrayOf(76f, 18f, 158f), floatArrayOf(92f, 12f, 132f),
                floatArrayOf(102f, 14f, 146f), floatArrayOf(114f, 12f, 152f))) {
                rr(b[0], b[2], b[1], 222f - b[2], 1.5f, bld)
                var y = b[2] + 6f
                while (y < 205f) {
                    lit.alpha = (255 * night * (if (j % 3 == 0) .3f else .9f)).toInt()
                    c.drawRect(b[0] + 2.5f, y, b[0] + 5.5f, y + 4f, lit); j++
                    lit.alpha = (255 * night * (if (j % 3 == 0) .3f else .9f)).toInt()
                    c.drawRect(b[0] + 7.5f, y, b[0] + 10.5f, y + 4f, lit); j++
                    y += 9f
                }
            }
            if (w.fog > 0f) c.drawRect(6f, 40f, 118f, 214f, fill(al(mix(bot, rgb(0xD4D8E0), .65f), w.fog)))
            if (w.rain) {
                val drop = stroke(al(rgb(0xD6E4FF), .5f), .8f)
                val r2 = Random(5)
                repeat(70) { val x = 6f + r2.nextFloat() * 120f; val y = 40f + r2.nextFloat() * 170f; c.drawLine(x, y, x - 2f, y + 9f, drop) }
            }
            c.restore()
            // frame, bars, sill
            val frameCol = mix(rgb(0xE8E8EE), rgb(0x0E1224), dk * .88f)
            val frame = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addPath(poly(-2f, 31f, 125f, 80f, 125f, 203f, -2f, 226f)); addPath(win)
            }
            val fp = fill(frameCol)
            c.drawPath(frame, fp)
            c.drawPath(poly(60f, 62f, 64f, 63.6f, 64f, 204f, 60f, 205f), fp)
            c.drawPath(poly(6f, 125f, 118f, 138.6f, 118f, 142.6f, 6f, 129f), fp)
            c.drawPath(poly(-4f, 219f, 124f, 198f, 128f, 205f, -6f, 228f), fp)
        }

        private fun curtain() {
            c.drawLine(92f, 40f, 130f, 56f, stroke(rgb(0x15161D), 3f))
            val base = intArrayOf(0x6A6F86, 0x555A70, 0x666B82)
            val xs = floatArrayOf(96f, 106f, 116f, 126f)
            val yt = floatArrayOf(49f, 54.7f, 60.3f, 65.8f)
            val yb = floatArrayOf(272.8f, 267.3f, 261.7f, 256.2f)
            for (i in 0..2) {
                c.drawPath(poly(xs[i], yt[i], xs[i + 1], yt[i + 1], xs[i + 1], yb[i + 1], xs[i], yb[i]), fill(mix(rgb(base[i]), rgb(0x0C0F1C), dk * .85f)))
            }
        }

        // ---- things that glow (drawn above the night darkening) ----
        private fun lightsRoom() {
            // ceiling LED
            val led = stroke(rgb(0xFF2BD6), 3f)
            c.drawLine(130f, 62f, 510f, 62f, led); c.drawLine(130f, 62f, 0f, -12f, led); c.drawLine(510f, 62f, 640f, -12f, led)
            // neon sign
            glow(173f, 126f, 60f, 26f, rgb(0xFF2BD6), .1f + dk * .45f)
            val neon = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; style = Paint.Style.STROKE }
            neon.strokeWidth = 4f; neon.color = al(rgb(0xFF2BD6), .25f); c.drawText("PLAYER 1", 173f, 128f, neon)
            neon.strokeWidth = 1f; neon.color = rgb(0xFFD1F6); c.drawText("PLAYER 1", 173f, 128f, neon)
            c.drawLine(148f, 135f, 198f, 135f, stroke(rgb(0x22D3EE), 1.6f))
            // TV
            glow(176f, 182f, 56f, 30f, rgb(0x8A5CFF), .1f + dk * .6f)
            c.save(); c.clipPath(poly(144f, 162f, 208f, 162f, 208f, 198f, 144f, 198f))
            c.drawRect(144f, 162f, 208f, 198f, shade(lin(0f, 162f, 0f, 198f, rgb(0x0D1A4A), rgb(0xFF7A3A))))
            c.drawCircle(176f, 174f, 6f, fill(rgb(0xFFCF5A)))
            c.drawPath(poly(144f, 198f, 168f, 178f, 184f, 178f, 208f, 198f), fill(rgb(0x14081F)))
            c.drawLine(176f, 179f, 176f, 198f, stroke(rgb(0xFFD23A), 1.2f).apply { pathEffect = DashPathEffect(floatArrayOf(3f, 3f), 0f) })
            rr(146f, 164f, 14f, 2f, 1f, fill(rgb(0x22D3EE)))
            c.restore()
            rr(140f, 228f, 72f, 2f, 1f, rgbBar(140f, 72f)); c.drawCircle(198f, 210f, 1f, fill(rgb(0x7CFF6A)))
        }

        private fun lightsDesk() {
            val g = .1f + dk * .6f
            glow(440f, 148f, 150f, 30f, rgb(0x22D3EE), g); glow(450f, 182f, 120f, 50f, rgb(0x8A5CFF), g)
            rr(304f, 146f, 270f, 3f, 1.5f, fill(rgb(0x22D3EE))); rr(342f, 231f, 232f, 2f, 1f, rgbBar(342f, 232f))
            // main monitor
            rr(394f, 152f, 116f, 48f, 1.5f, shade(lin(394f, 152f, 510f, 200f, rgb(0x1A0F3C), rgb(0x0B4A96))))
            c.drawPath(Path().apply { moveTo(394f, 200f); lineTo(394f, 184f); lineTo(418f, 170f); lineTo(436f, 182f); lineTo(458f, 164f); lineTo(480f, 180f); lineTo(510f, 168f); lineTo(510f, 200f); close() }, fill(al(rgb(0x0A0620), .75f)))
            c.drawCircle(486f, 166f, 7f, fill(al(rgb(0xFFE27A), .85f)))
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 15f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
            c.drawText(clock, 452f, 187f, t)
            // side monitor
            rr(352f, 150f, 28f, 60f, 1.5f, fill(rgb(0x150A2E)))
            val lines = arrayOf(intArrayOf(156, 12, 0xFF2BD6), intArrayOf(164, 22, 0x6A7AFF), intArrayOf(172, 17, 0x22D3EE), intArrayOf(180, 21, 0x6A7AFF), intArrayOf(188, 14, 0xFF2BD6))
            for (l in lines) rr(355f, l[0].toFloat(), l[1].toFloat(), 3f, 1.5f, fill(rgb(l[2])))
            // tower fans, keyboard light, mouse mat edge
            c.drawCircle(539f, 183f, 7f, stroke(rgb(0x22D3EE), 2f)); c.drawCircle(539f, 183f, 2.5f, fill(rgb(0x22D3EE)))
            c.drawCircle(539f, 201f, 7f, stroke(rgb(0xFF2BD6), 2f)); c.drawCircle(539f, 201f, 2.5f, fill(rgb(0xFF2BD6)))
            rr(548f, 206f, 14f, 6f, 2f, fill(rgb(0x1D2030)))
            rr(399f, 217f, 64f, 1.6f, 0.8f, rgbBar(399f, 64f)); rr(390f, 225f, 110f, 1.4f, .7f, rgbBar(390f, 110f))
        }
    }
}
