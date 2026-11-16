package com.fares.glass.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.graphics.Path
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The desk wallpaper's room, with the same numbers as gfx/DeskWallpaper.kt.
 * Everything is measured in pixels of the 1280 x 720 wallpaper picture.
 * x = meters across the room, hm = height in meters, z = depth (0 = near, 1 = far wall).
 */
internal object DeskRoom {
    const val W = 1280f
    const val H = 720f
    private const val VX = W * 0.72f
    private const val VY = H * 0.48f
    private const val FAR = 0.42f
    const val M = H / 3f

    /** Meters of room depth for one unit of z. */
    const val ZM = 3.3f

    /** Width of the room in meters. */
    const val XM = W / M

    fun sc(z: Float): Float = 1f - z * (1f - FAR)
    fun rx(xm: Float, z: Float): Float = VX + (xm * M - VX) * sc(z)
    fun ry(hm: Float, z: Float): Float = VY + (H - hm * M - VY) * sc(z)

    val bx0: Float = rx(0f, 1f)
    val bx1: Float = rx(XM, 1f)
    val by0: Float = ry(3f, 1f)
    val by1: Float = ry(0f, 1f)

    /** A flat shape given as triples: x (meters), height (meters), depth z. */
    fun path(vararg v: Float): Path {
        val p = Path()
        var i = 0
        while (i + 2 < v.size) {
            val x = rx(v[i], v[i + 2])
            val y = ry(v[i + 1], v[i + 2])
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            i += 3
        }
        p.close()
        return p
    }
}

/** A picture already bent onto a wall. Draw it at (left, top) in wallpaper pixels, shrunk by [scale]. */
internal class Warped(val bitmap: Bitmap, val left: Float, val top: Float, val scale: Float)

/**
 * Bends [src] onto the left wall (the plane x = [xm]) between depths [z0]..[z1] and heights [h0]..[h1],
 * the same way the shelves are drawn: vertical strips that get shorter towards the back of the room.
 */
internal fun warpToWall(
    src: Bitmap, xm: Float, z0: Float, z1: Float, h0: Float, h1: Float, scale: Float, strips: Int = 100,
): Warped {
    val left = DeskRoom.rx(xm, z0)
    val right = DeskRoom.rx(xm, z1)
    val top = min(DeskRoom.ry(h1, z0), DeskRoom.ry(h1, z1))
    val bottom = max(DeskRoom.ry(h0, z0), DeskRoom.ry(h0, z1))
    val w = max(1, ceil((right - left) * scale).toInt())
    val h = max(1, ceil((bottom - top) * scale).toInt())
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    val sw = src.width
    val sh = src.height
    for (i in 0 until strips) {
        val za = z0 + (z1 - z0) * i / strips
        val zb = z0 + (z1 - z0) * (i + 1) / strips
        val zm = (za + zb) / 2f
        val xa = DeskRoom.rx(xm, za)
        val xb = DeskRoom.rx(xm, zb)
        val yT = DeskRoom.ry(h1, zm)
        val yB = DeskRoom.ry(h0, zm)
        val s0 = sw * i / strips
        val s1 = max(s0 + 1, sw * (i + 1) / strips)
        canvas.drawBitmap(
            src, Rect(s0, 0, s1, sh),
            RectF((xa - left) * scale, (yT - top) * scale, (xb - left) * scale + 1f, (yB - top) * scale),
            paint,
        )
    }
    return Warped(out, left, top, scale)
}
