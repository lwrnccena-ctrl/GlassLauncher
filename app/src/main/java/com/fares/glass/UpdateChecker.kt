package com.fares.glass

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {
    private const val API =
        "https://api.github.com/repos/lwrnccena-ctrl/GlassLauncher/releases/latest"

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
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.getString("name").endsWith(".apk")) {
                        apkUrl = a.getString("browser_download_url")
                        break
                    }
                }
                val current = activity.packageManager
                    .getPackageInfo(activity.packageName, 0).versionName ?: "0"

                activity.runOnUiThread {
                    if (activity.isFinishing) return@runOnUiThread
                    if (apkUrl != null && isNewer(latest, current)) {
                        showDialog(activity, latest, apkUrl)
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

    private fun showDialog(activity: Activity, version: String, url: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update available")
            .setMessage("Glass $version is ready. Install it now?")
            .setPositiveButton("Update") { _, _ -> download(activity, url) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun download(activity: Activity, url: String) {
        toast(activity, "Downloading update...")
        thread {
            try {
                val dir = File(activity.cacheDir, "updates")
                dir.mkdirs()
                val file = File(dir, "Glass-update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.inputStream.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                activity.runOnUiThread { install(activity, file) }
            } catch (e: Exception) {
                activity.runOnUiThread { toast(activity, "Download failed") }
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
