package com.fares.glass

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Looks for a newer release on GitHub and shows a "Version X is ready" card with the release notes.
 *
 * The "What's new" list is the description of the GitHub release: write one change per line,
 * each line starting with "- ", for example:
 *   - Stream search
 *   - Spotify playlists
 */
object UpdateChecker {
    private const val API =
        "https://api.github.com/repos/lwrnccena-ctrl/GlassLauncher/releases/latest"

    private class Release(
        val version: String,
        val apkUrl: String,
        val apkSize: Long,
        val notes: List<String>,
    )

    fun check(activity: Activity, showNoUpdate: Boolean = false) {
        thread {
            try {
                val conn = URL(API).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val latest = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                var apkSize = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        apkUrl = a.getString("browser_download_url")
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
                val notes = parseNotes(json.optString("body", ""))
                val current = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).versionName ?: "0"

                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    if (apkUrl != null && isNewer(latest, current)) {
                        showDialog(activity, current, Release(latest, apkUrl, apkSize, notes))
                    } else if (showNoUpdate) {
                        toast(activity, "Glass is up to date ($current)")
                    }
                }
            } catch (e: Exception) {
                if (showNoUpdate) {
                    activity.runOnUiThread { toast(activity, "Could not check for updates") }
                }
            }
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The release description as a list of short lines (the lines that start with "-", "*" or a bullet). */
    private fun parseNotes(body: String): List<String> {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val bullets = lines.filter { it.startsWith("-") || it.startsWith("*") || it.startsWith("\u2022") }
        val source = if (bullets.isNotEmpty()) bullets else lines.filter { !it.startsWith("#") }
        return source
            .map { it.trimStart('-', '*', '\u2022', ' ').replace("**", "").replace("`", "").trim() }
            .filter { it.isNotEmpty() }
            .take(8)
    }

    // ---- The card ----

    private fun showDialog(activity: Activity, current: String, r: Release) {
        // Sizes follow the screen width (the launcher is designed for a 960 dp wide screen).
        val d = activity.resources.displayMetrics.widthPixels / 960f
        fun px(v: Number): Int = (v.toFloat() * d).toInt()

        fun label(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_PX, size * d)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        fun pill(focused: Boolean) = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xFFE91E4F.toInt(), 0xFFFF5A1F.toInt(), 0xFFFFB033.toInt()),
        ).apply {
            cornerRadius = px(100).toFloat()
            if (focused) setStroke(px(3), Color.WHITE)
        }

        fun focusable(v: View, scale: Float, background: (Boolean) -> Drawable?) {
            v.isFocusable = true
            v.background = background(false)
            v.setOnFocusChangeListener { view, focused ->
                view.background = background(focused)
                val s = if (focused) scale else 1f
                view.animate().scaleX(s).scaleY(s).setDuration(120).start()
            }
        }

        val white = Color.WHITE
        val softWhite = Color.argb(150, 255, 255, 255)
        val accent = 0xFFFFC27A.toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(28), px(26), px(28), px(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF6E3028.toInt(), 0xFF5E2442.toInt(), 0xFF3A2150.toInt()),
            ).apply {
                cornerRadius = px(28).toFloat()
                setStroke(px(1), Color.argb(70, 255, 255, 255))
            }
        }

        // Header: icon, title, "You have ... · ... MB download"
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val glyph = DownloadGlyph(activity).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFE91E4F.toInt(), 0xFFFF5A1F.toInt(), 0xFFFFB033.toInt()),
            ).apply { cornerRadius = px(16).toFloat() }
        }
        header.addView(glyph, LinearLayout.LayoutParams(px(56), px(56)).apply { marginEnd = px(16) })
        val titles = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label("Version ${r.version} is ready", 22f, white, bold = true))
        val size = if (r.apkSize > 0) " \u00B7 " + String.format(Locale.US, "%.1f MB download", r.apkSize / 1048576.0) else ""
        titles.addView(label("You have $current$size", 13f, softWhite))
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)

        // What's new
        if (r.notes.isNotEmpty()) {
            val heading = label("WHAT'S NEW", 12f, accent, bold = true).apply { letterSpacing = 0.06f }
            root.addView(heading, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(22) })
            r.notes.forEach { line ->
                val row = label("\u2022   $line", 15f, Color.argb(235, 255, 255, 255)).apply { maxLines = 2 }
                root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) })
            }
        }

        // Download and install
        val installBtn = label("Download and install", 16f, white, bold = true).apply {
            gravity = Gravity.CENTER
            isClickable = true
        }
        focusable(installBtn, 1.03f) { pill(it) }
        root.addView(installBtn, LinearLayout.LayoutParams(-1, px(52)).apply { topMargin = px(24) })

        // Footer: "Checked just now" and "Check again"
        val footer = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(
            label("Checked just now", 12f, Color.argb(120, 255, 255, 255)),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val again = label("Check again", 13f, accent, bold = true).apply {
            setPadding(px(12), px(6), px(12), px(6))
            isClickable = true
        }
        focusable(again, 1f) { focused ->
            if (focused) GradientDrawable().apply {
                setColor(Color.argb(50, 255, 255, 255))
                cornerRadius = px(14).toFloat()
            } else null
        }
        footer.addView(again, LinearLayout.LayoutParams(-2, -2))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(14) })

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val cancelled = AtomicBoolean(false)
        dialog.setOnDismissListener { cancelled.set(true) }

        var busy = false
        installBtn.setOnClickListener {
            if (busy) return@setOnClickListener
            busy = true
            installBtn.text = "Downloading\u2026 0%"
            download(
                activity, r.apkUrl, r.apkSize, cancelled,
                onProgress = { p -> activity.runOnUiThread { installBtn.text = "Downloading\u2026 $p%" } },
                onDone = { file ->
                    activity.runOnUiThread {
                        dialog.setOnDismissListener(null)
                        dialog.dismiss()
                        install(activity, file)
                    }
                },
                onError = {
                    activity.runOnUiThread {
                        busy = false
                        installBtn.text = "Download failed. Try again"
                    }
                },
            )
        }
        again.setOnClickListener {
            dialog.dismiss()
            check(activity, showNoUpdate = true)
        }

        dialog.show()
        dialog.window?.setLayout(px(540), ViewGroup.LayoutParams.WRAP_CONTENT)
        installBtn.requestFocus()
    }

    /** The white download arrow on the orange icon. */
    private class DownloadGlyph(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            paint.strokeWidth = w * 0.06f
            val cx = w / 2f
            canvas.drawLine(cx, h * 0.26f, cx, h * 0.60f, paint)
            canvas.drawLine(cx - w * 0.13f, h * 0.47f, cx, h * 0.61f, paint)
            canvas.drawLine(cx + w * 0.13f, h * 0.47f, cx, h * 0.61f, paint)
            canvas.drawLine(w * 0.30f, h * 0.74f, w * 0.70f, h * 0.74f, paint)
        }
    }

    // ---- Download and install ----

    private fun download(
        activity: Activity,
        url: String,
        expected: Long,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
        onDone: (File) -> Unit,
        onError: () -> Unit,
    ) {
        thread {
            try {
                val dir = File(activity.cacheDir, "updates")
                dir.mkdirs()
                val file = File(dir, "Glass-update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                val total = if (expected > 0) expected else conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var done = 0L
                        var last = -1
                        while (true) {
                            if (cancelled.get()) return@thread
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) {
                                val p = (done * 100 / total).toInt().coerceIn(0, 100)
                                if (p != last) {
                                    last = p
                                    onProgress(p)
                                }
                            }
                        }
                    }
                }
                onDone(file)
            } catch (e: Exception) {
                onError()
            }
        }
    }

    private fun install(activity: Activity, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= 26 &&
                !activity.packageManager.canRequestPackageInstalls()
            ) {
                toast(activity, "Allow Glass to install apps, then try again")
                activity.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
                return
            }
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            toast(activity, "Could not start the install")
        }
    }

    private fun toast(activity: Activity, text: String) {
        Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    }
}
