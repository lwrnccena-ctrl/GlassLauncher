package com.fares.glass.gfx

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Custom app icons for Glass.
 *
 * Two sources, checked in this order:
 * 1. The selected icon pack (standard ADW / Nova / GO format, with an appfilter.xml).
 * 2. A sideloaded PNG: /sdcard/Glass/icons/<package.name>.png
 */
object CustomIcons {
    private const val TAG = "Glass"

    private val PACK_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
    )

    @Volatile private var packPkg: String? = null

    /** "package/activity" -> drawable name inside the pack. Replaced as a whole, never edited in place. */
    @Volatile private var mapping: Map<String, String> = emptyMap()

    /** Installed icon packs: package name -> label. */
    fun listPacks(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, String>()
        for (action in PACK_ACTIONS) {
            val found = runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
            for (ri in found) {
                out[ri.activityInfo.packageName] = ri.loadLabel(pm).toString()
            }
        }
        return out
    }

    /** Currently selected pack (package name), or null. */
    fun selectedPack(): String? = packPkg

    /** Call at startup and whenever the user picks a pack. Pass null for "no pack". */
    fun selectPack(ctx: Context, pkg: String?) {
        if (pkg == null) {
            packPkg = null
            mapping = emptyMap()
            return
        }
        val newMap = HashMap<String, String>()
        try {
            val res = ctx.packageManager.getResourcesForApplication(pkg)
            val parser = try {
                res.assets.openXmlResourceParser("appfilter.xml")
            } catch (e: Exception) {
                val id = res.getIdentifier("appfilter", "xml", pkg)
                if (id == 0) null else res.getXml(id)
            }
            if (parser != null) {
                try {
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "item") {
                            val component = parser.getAttributeValue(null, "component") ?: continue
                            val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                            val key = component.removePrefix("ComponentInfo{").removeSuffix("}")
                            newMap[key] = drawable
                        }
                    }
                } finally {
                    parser.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "icon pack load failed: $pkg", e)
        }
        packPkg = pkg
        mapping = newMap
    }

    /** A custom icon for this app, or null (then Glass uses its normal artwork). */
    fun get(ctx: Context, component: ComponentName): Drawable? {
        // 1) Icon pack
        val pkg = packPkg
        if (pkg != null) {
            val name = mapping[component.flattenToString()]
            if (name != null) {
                try {
                    val res = ctx.packageManager.getResourcesForApplication(pkg)
                    val id = res.getIdentifier(name, "drawable", pkg)
                    if (id != 0) return res.getDrawable(id, null)
                } catch (_: Exception) {
                }
            }
        }
        // 2) Sideloaded PNG
        val f = File("/sdcard/Glass/icons/${component.packageName}.png")
        if (f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.path)
            if (bmp != null) return BitmapDrawable(ctx.resources, bmp)
        }
        return null
    }
}
