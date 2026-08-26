package com.conreo.couchytv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import java.io.File

data class IconPackInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

/**
 * Reads community icon packs that follow the Nova/ADW/Projectivy
 * `appfilter.xml` convention — the de-facto standard used by the whole
 * Android icon-pack ecosystem, so any pack from F-Droid or elsewhere (e.g.
 * ProjectivyIconPack) works without launcher-specific packaging.
 */
object IconPack {

    // The intent actions the ecosystem's icon packs declare so launchers can
    // find them; querying all four covers Nova, ADW, Apex/Action and Go.
    private val MARKER_ACTIONS = listOf(
        "com.novalauncher.THEME",
        "org.adw.launcher.THEMES",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    fun installedIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val seen = LinkedHashSet<String>()
        for (action in MARKER_ACTIONS) {
            for (ri in pm.queryIntentActivities(Intent(action), 0)) {
                ri.activityInfo?.packageName?.let { seen.add(it) }
            }
        }
        return seen.map { pkg ->
            val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                .getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            IconPackInfo(pkg, label, icon)
        }
    }

    /**
     * package name -> drawable resource name, parsed from the pack's
     * appfilter. Two locations are both part of the real-world convention:
     * the original ADW spec compiles it as a resource at res/xml/appfilter
     * (many generators — e.g. Blueprint-based packs like ProjectivyIconPack —
     * only ever populate this one, leaving assets/appfilter.xml a stale,
     * unused template default), while other tools write a plain-text asset
     * at assets/appfilter.xml instead. Try the compiled resource first.
     */
    fun appFilterMap(context: Context, packPkg: String): Map<String, String> {
        val pm = context.packageManager
        val packRes = runCatching { pm.getResourcesForApplication(packPkg) }.getOrNull() ?: return emptyMap()

        val resId = packRes.getIdentifier("appfilter", "xml", packPkg)
        if (resId != 0) {
            val fromRes = runCatching {
                val parser = packRes.getXml(resId)
                try { parseItems(parser) } finally { parser.close() }
            }.getOrNull().orEmpty()
            if (fromRes.isNotEmpty()) return fromRes
        }

        val assets = runCatching { packRes.assets.open("appfilter.xml") }.getOrNull() ?: return emptyMap()
        return assets.use { stream ->
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            parseItems(parser)
        }
    }

    private fun parseItems(parser: XmlPullParser): Map<String, String> {
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    // component looks like ComponentInfo{some.pkg/some.pkg.SomeActivity}
                    val pkg = component?.substringAfter('{')?.substringBefore('/')
                    if (pkg != null && drawable != null) map[pkg] = drawable
                }
            }
            event = parser.next()
        }
        return map
    }

    /**
     * Resolves a pack's drawable by name the same way for every caller: a
     * package context themed with the pack's own manifest theme (needed for
     * ?attr/... colors in themed vector/adaptive drawables — createPackageContext()'s
     * .theme starts blank and does NOT pick this up on its own), looked up
     * against its *compiled* resources. There is no separate "browse all
     * icons" resource in the appfilter convention — the appfilter mapping
     * itself (see [appFilterMap]) is the only reliable catalog of what a pack
     * ships, so browsing/searching goes through it, by target app, everywhere.
     */
    private fun loadPackDrawable(context: Context, packPkg: String, drawableName: String): android.graphics.drawable.Drawable? {
        val pm = context.packageManager
        val packContext = runCatching {
            context.createPackageContext(packPkg, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return null
        val themeRes = runCatching { pm.getApplicationInfo(packPkg, 0).theme }.getOrDefault(0)
        if (themeRes != 0) packContext.setTheme(themeRes)
        val packRes = packContext.resources
        val id = packRes.getIdentifier(drawableName, "drawable", packPkg)
        if (id == 0) return null
        return runCatching {
            androidx.core.content.res.ResourcesCompat.getDrawable(packRes, id, packContext.theme)
        }.getOrNull()
    }

    // The appfilter format has no concept of a card/icon background at all
    // (confirmed against ProjectivyIconPack's real, decompiled appfilter.xml
    // — no <iconback>/<iconupon> masking tags, no dedicated color/style
    // resource, and its manifest android:theme is only a splash-screen
    // theme). A launcher-supplied backdrop for transparent logo-only pack
    // art is standard practice industry-wide (phone launchers just skip a
    // backdrop entirely; TV launchers like Projectivy's own draw one) — so
    // this reuses Couchy's own established dark surface color (Theme.kt's
    // colorScheme.surface, opaque) rather than inventing an unrelated one.
    private const val CARD_BACKGROUND = 0xFF1C1E24.toInt()
    private const val BANNER_W = 480
    private const val BANNER_H = 270

    /**
     * The appfilter format has only one drawable per app and no separate
     * slot for the leanback banner this launcher otherwise shows. Many packs
     * (e.g. ProjectivyIconPack) ship that drawable as a transparent logo
     * only, meant to be composited onto a card — used bare, the launcher's
     * own per-app tile color showed through the transparent margins
     * instead. So this always composites the logo, fit (never cropped or
     * stretched) and centered, onto a fixed dark card the same size as a
     * real banner, and returns that as the banner.
     */
    fun loadPackBanner(context: Context, packPkg: String, drawableName: String): Bitmap? {
        val drawable = loadPackDrawable(context, packPkg, drawableName) ?: return null
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: 128
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: 128
        val logo = runCatching { drawable.toBitmap(iw, ih) }.getOrNull() ?: return null

        val cacheDir = File(context.filesDir, "iconpack").apply { mkdirs() }
        // v6: bumped after switching to composite-onto-a-dark-card, replacing
        // the earlier icon/banner classification guesswork entirely.
        return AppRepository.cachedBitmap(cacheDir, "$packPkg-$drawableName-banner-v6.webp", Bitmap.Config.ARGB_8888) {
            compositeOnCard(logo)
        }
    }

    /**
     * Scales [logo] to fit (never cropped or stretched) and centers it on the
     * same dark card [loadPackBanner] uses — reused wherever a square source
     * (an app's own icon, when it has no banner of its own) needs to become
     * TV banner art.
     */
    fun compositeOnCard(logo: Bitmap): Bitmap {
        // Fit within the card with a margin, so logos never touch the edge.
        val padW = BANNER_W - 64
        val padH = BANNER_H - 48
        val scale = minOf(padW.toFloat() / logo.width, padH.toFloat() / logo.height, 1f)
        val rw = (logo.width * scale).toInt().coerceAtLeast(1)
        val rh = (logo.height * scale).toInt().coerceAtLeast(1)
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(logo, rw, rh, true) else logo

        val card = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(card).apply {
            drawColor(CARD_BACKGROUND)
            drawBitmap(scaled, (BANNER_W - rw) / 2f, (BANNER_H - rh) / 2f, null)
        }
        return card
    }
}
