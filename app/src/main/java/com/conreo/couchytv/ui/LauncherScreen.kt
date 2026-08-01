package com.conreo.couchytv.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.conreo.couchytv.Actions
import com.conreo.couchytv.data.AppEntry
import com.conreo.couchytv.data.AppRepository
import com.conreo.couchytv.data.ConfigStore
import com.conreo.couchytv.data.LAYOUT_DOCK
import com.conreo.couchytv.data.LAYOUT_GRID
import com.conreo.couchytv.data.LauncherConfig
import com.conreo.couchytv.data.NetStatus
import com.conreo.couchytv.data.networkStatusFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Icon size steps (card width) — 5 levels for fine control. */
val ICON_SIZES = listOf(120.dp, 150.dp, 190.dp, 230.dp, 270.dp)

/** Allocated ONCE — recomposition never rebuilds the scrim brushes. */
// Full: darkens the whole wallpaper evenly.
private val ScrimFull = Brush.verticalGradient(
    listOf(
        Color.Black.copy(alpha = 0.55f),
        Color.Black.copy(alpha = 0.25f),
        Color.Black.copy(alpha = 0.45f),
    )
)
// Top & bottom: dark bands only where the status bar and dock/labels sit; the
// middle of the wallpaper stays fully clear (video/aerials keep their punch).
private val ScrimTopBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.16f to Color.Black.copy(alpha = 0f),
    0.82f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)
// Top only — dark band under the status bar, rest clear.
private val ScrimTop = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.6f),
    0.20f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0f),
)
// Bottom only — dark band under the dock/labels, rest clear.
private val ScrimBottom = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0f),
    0.80f to Color.Black.copy(alpha = 0f),
    1.0f to Color.Black.copy(alpha = 0.5f),
)
/** Spacing steps between cards — 5 levels. */
val GAP_SIZES = listOf(4.dp, 10.dp, 16.dp, 24.dp, 32.dp)
/** Video wallpaper playback speeds (index stored in config.videoSpeed). */
val VIDEO_SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f)
/** Icon corner roundness steps (index stored in config.cornerRadius). */
val CORNER_RADII = listOf(0.dp, 4.dp, 10.dp, 18.dp, 28.dp)
/** Manual whole-UI scale steps (config.uiScale 1..5). <1 = more compact. */
val UI_SCALES = listOf(0.75f, 0.9f, 1.0f, 1.15f, 1.3f)

/** The effective UI scale: 0 = Auto (compact high-DPI TVs down to ~1200dp of
 *  width so every device reads the same, never enlarging roomy ones); 1..5 pick
 *  a fixed value from [UI_SCALES]. */
fun uiScaleFactor(index: Int, screenWidthDp: Int): Float =
    if (index <= 0) (screenWidthDp / 1200f).coerceIn(0.6f, 1.0f)
    else UI_SCALES[(index - 1).coerceIn(0, UI_SCALES.size - 1)]

/** Global text boost (sp only, not dp): 1 = off. Raise to enlarge every label
 *  without touching icons or layout. */
const val FONT_BOOST = 1f

/** Renders [content] at [scale]× the device density — one knob shrinks/grows
 *  every dp and sp uniformly (icons, text, settings, wizard). Also rescales the
 *  Configuration so width-derived layout (dock/grid columns) stays correct. */
@Composable
fun ScaledUi(scale: Float, content: @Composable () -> Unit) {
    val base = androidx.compose.ui.platform.LocalDensity.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val scaledCfg = remember(cfg, scale) {
        android.content.res.Configuration(cfg).apply {
            screenWidthDp = (cfg.screenWidthDp / scale).toInt()
            screenHeightDp = (cfg.screenHeightDp / scale).toInt()
            smallestScreenWidthDp = (cfg.smallestScreenWidthDp / scale).toInt()
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(base.density * scale, base.fontScale * FONT_BOOST),
        androidx.compose.ui.platform.LocalConfiguration provides scaledCfg,
    ) { content() }
}
// compositionLocalOf (not static): roundness changes at runtime and must
// recompose every reader, including those inside subcompositions like the
// dock's BoxWithConstraints (its 2nd-row peek) — static wouldn't reach them.
val LocalCornerRadius = androidx.compose.runtime.compositionLocalOf { 10.dp }

/**
 * How many full-size cards fit in [available] width, and the spacing to use.
 * Icons NEVER shrink: if the requested gap doesn't fit, the gap is squeezed
 * instead so every icon keeps its exact size.
 */
fun fitRow(available: Dp, cardWidth: Dp, gap: Dp): Pair<Int, Dp> {
    val cols = (((available + gap) / (cardWidth + gap)).toInt()).coerceAtLeast(1)
    val leftover = available - cardWidth * cols
    val used = if (cols > 1 && leftover > 0.dp) {
        val spread = leftover / (cols - 1)
        if (spread < gap) spread else gap
    } else 0.dp
    return cols to used
}

/** Unwrap a (possibly wrapped) Context to its host Activity, or null. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun LauncherApp(rescanTick: Int) {
    val context = LocalContext.current
    val store = remember { ConfigStore(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Wait for the persisted config before drawing anything, so the setup
    // wizard never flashes for users who already completed it.
    val configOrNull by produceState<LauncherConfig?>(initialValue = null) {
        store.flow.collect { value = it }
    }
    // Kick off the app scan on the FIRST composition — concurrent with the
    // DataStore config read, not serialized after it (scan takes no config).
    // Warm relaunches start from the in-memory cache (instant); a fresh scan
    // still runs to pick up changes. Not rendered until past the gates below.
    val appsOrNull by produceState(initialValue = AppRepository.memoryCache, rescanTick) {
        value = withContext(Dispatchers.Default) { AppRepository.scan(context.applicationContext) }
    }
    val config = configOrNull ?: run {
        LoadingScreen()
        return
    }

    val uiScale = uiScaleFactor(
        config.uiScale,
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp,
    )

    // Mirror the chosen language to SharedPreferences (which attachBaseContext
    // reads synchronously) and recreate the activity when it changes, so the new
    // locale takes effect without a restart.
    val activity = remember(context) { context.findActivity() }
    LaunchedEffect(config.language) {
        if (com.conreo.couchytv.MainActivity.currentLocalePref(context) != config.language) {
            com.conreo.couchytv.MainActivity.persistLocale(context, config.language)
            activity?.recreate()
        }
    }
    // The SurfaceView video wallpaper needs a transparent window to show through;
    // every other mode draws an opaque wallpaper, so keep the window opaque then
    // (a transparent window would let whatever is behind the home leak through).
    val videoWallpaper = config.useBuiltinAerials ||
        (config.useVideoWallpaper && config.videoUri.isNotEmpty())
    LaunchedEffect(activity, videoWallpaper) {
        activity?.window?.setBackgroundDrawable(
            if (videoWallpaper) null
            else android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)
        )
    }

    if (!config.setupDone) {
        ScaledUi(uiScale) {
            LiteTvTheme(accent = ACCENTS[0]) {
                SetupWizard(
                    onDone = {
                        scope.launch { store.update { it.copy(setupDone = true) } }
                    },
                    onVpnChosen = { pkg ->
                        scope.launch {
                            store.update {
                                // Picked an app → VPN button opens it; skipped → hide it.
                                if (pkg == null) it.copy(showVpnButton = false, vpnApp = "")
                                else it.copy(showVpnButton = true, vpnApp = pkg)
                            }
                        }
                    },
                )
            }
        }
        return
    }

    // Hold the sober loading screen until the launcher can render COMPLETE —
    // every app scanned and every icon decoded.
    val apps = appsOrNull ?: run {
        LoadingScreen()
        return
    }
    // flowOn(IO): the callbackFlow's initial compute() does ConnectivityManager
    // binder calls — keep them off the main thread on the first real frame.
    val net by remember { networkStatusFlow(context).flowOn(Dispatchers.IO) }
        .collectAsStateWithLifecycle(initialValue = NetStatus())

    // Bump on every ON_RESUME so the clock/date refresh immediately after
    // sleep — they normally only tick on minute boundaries via produceState.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Format with the app's current locale (reflects the language override, and
    // recomposes on change) — not Locale.getDefault(), which the framework can
    // reset, leaving the date stuck in the previous language.
    val locale = androidx.core.os.ConfigurationCompat
        .getLocales(androidx.compose.ui.platform.LocalConfiguration.current)
        .get(0) ?: Locale.getDefault()
    val time by produceState(initialValue = "", config.h24, locale, resumeTick) {
        val fmt = SimpleDateFormat(if (config.h24) "HH:mm" else "h:mm a", locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val date by produceState(initialValue = "", config.dateFormat, locale, resumeTick) {
        val pattern = com.conreo.couchytv.data.DATE_FORMATS
            .getOrElse(config.dateFormat) { "" }
        if (pattern.isEmpty()) { value = ""; return@produceState }
        val fmt = SimpleDateFormat(pattern, locale)
        while (true) {
            value = fmt.format(Date())
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    // New installs are auto-added to the FIRST section.
    LaunchedEffect(apps) {
        if (apps.isEmpty()) return@LaunchedEffect
        val current = apps.map { it.pkg }.toSet()
        store.update { cfg ->
            if (cfg.knownApps.isEmpty()) cfg.copy(knownApps = current)
            else {
                val added = current - cfg.knownApps
                if (added.isEmpty() && current == cfg.knownApps) cfg
                else {
                    val first = cfg.categories.first().id
                    cfg.copy(
                        knownApps = current,
                        sections = cfg.sections + added.associateWith { setOf(first) },
                    )
                }
            }
        }
    }

    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var movePkg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var wallpaperVersion by remember { mutableIntStateOf(0) }

    // Drop empty sections: an empty row is dead space AND a focus trap that
    // stops D-pad navigation from reaching the section below it.
    val categorized = remember(apps, config) {
        AppRepository.categorize(apps, config).filter { it.second.isNotEmpty() }
    }
    // The dock's flat app list (all sections concatenated, de-duped). Hoisted &
    // remembered so its identity stays stable across recompositions — otherwise a
    // fresh list on every clock tick blocks DockArea from strong-skipping.
    val dockApps = remember(categorized) {
        categorized.flatMap { it.second }.distinctBy { it.pkg }
    }
    val accent = ACCENTS[config.accent.coerceIn(0, ACCENTS.size - 1)]

    // ----- display dimensions: size + spacing drive everything, columns
    // are always derived from available width (never a fixed count) -----
    val cardWidth: Dp = ICON_SIZES[config.iconScale.coerceIn(0, ICON_SIZES.size - 1)]
    val gap: Dp = GAP_SIZES[config.spacing.coerceIn(0, GAP_SIZES.size - 1)]

    // Columns as laid out on screen (mirrors the padding used by GridSection /
    // DockArea) — move mode needs them to shift an icon up/down by a full row.
    val screenW: Dp = LocalConfiguration.current.screenWidthDp.dp
    val gridCols = fitRow(screenW - 88.dp, cardWidth, gap).first
    val dockCols = fitRow(screenW - 116.dp, cardWidth, gap).first

    var dockExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(config.layout) { if (config.layout != LAYOUT_DOCK) dockExpanded = false }

    // ----- move-mode helpers -----
    fun findMoving(): Triple<Int, List<AppEntry>, Int>? {
        val pkg = movePkg ?: return null
        categorized.forEachIndexed { catIndex, (_, list) ->
            val i = list.indexOfFirst { it.pkg == pkg }
            if (i >= 0) return Triple(catIndex, list, i)
        }
        return null
    }

    fun moveWithinRow(delta: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val j = i + delta
        if (j < 0 || j >= list.size) return
        val catId = categorized[catIndex].first.id
        val newOrder = list.map { it.pkg }.toMutableList().also { l ->
            val tmp = l[i]; l[i] = l[j]; l[j] = tmp
        }
        scope.launch { store.update { it.copy(order = it.order + (catId to newOrder)) } }
    }

    fun moveAcrossRows(delta: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val target = catIndex + delta
        if (target < 0 || target >= categorized.size) return
        val pkg = movePkg ?: return
        val app = apps.firstOrNull { it.pkg == pkg } ?: return
        val sourceId = categorized[catIndex].first.id
        val targetId = categorized[target].first.id
        val targetList = categorized[target].second
        if (targetList.any { it.pkg == pkg }) return // already in target section
        val insertAt = i.coerceAtMost(targetList.size)
        val newTargetOrder = targetList.map { it.pkg }.toMutableList().also { it.add(insertAt, pkg) }
        val newSourceOrder = list.map { it.pkg }.filter { it != pkg }
        scope.launch {
            store.update { cfg ->
                val effective = AppRepository.sectionsOf(app, cfg)
                cfg.copy(
                    sections = cfg.sections + (pkg to (effective - sourceId + targetId)),
                    order = cfg.order + (sourceId to newSourceOrder) + (targetId to newTargetOrder),
                )
            }
        }
    }

    // Reorder the moving app within its own section to a new index.
    fun reorderWithin(catIndex: Int, list: List<AppEntry>, from: Int, to: Int) {
        if (from == to) return
        val catId = categorized[catIndex].first.id
        val seq = list.map { it.pkg }.toMutableList()
        val p = seq.removeAt(from)
        seq.add(to.coerceIn(0, seq.size), p)
        scope.launch { store.update { it.copy(order = it.order + (catId to seq)) } }
    }

    // Grid: up/down move by a full row (±cols) inside the section; past the
    // top/bottom edge it hops to the neighbouring section.
    fun moveGridVertical(delta: Int, cols: Int) {
        val (catIndex, list, i) = findMoving() ?: return
        val size = list.size
        if (delta < 0) {
            if (i < cols) moveAcrossRows(-1) else reorderWithin(catIndex, list, i, i - cols)
        } else {
            val lastRowStart = ((size - 1) / cols) * cols
            if (i >= lastRowStart) moveAcrossRows(1)
            else reorderWithin(catIndex, list, i, minOf(i + cols, size - 1))
        }
    }

    // Dock: one flat sequence (all sections concatenated). Moving by [delta]
    // positions may cross a section block, so the moved app is re-homed into
    // its new neighbour's section and every affected order list is rebuilt from
    // the resulting sequence.
    // ponytail: a multi-section app (shown once in the dock) is re-homed only
    // for its displayed section; its other memberships are left as-is.
    fun moveDock(delta: Int) {
        val pkg = movePkg ?: return
        val fi = dockApps.indexOfFirst { it.pkg == pkg }
        if (fi < 0) return
        val target = fi + delta
        if (target < 0 || target >= dockApps.size) return
        val flat = dockApps.map { it.pkg }.toMutableList()
        flat.removeAt(fi)
        flat.add(target, pkg)

        val orderCats = config.categories.map { it.id }
        fun dockCatOf(p: String): String {
            val app = apps.firstOrNull { it.pkg == p } ?: return orderCats.last()
            val secs = AppRepository.sectionsOf(app, config)
            return orderCats.firstOrNull { it in secs } ?: orderCats.last()
        }
        val sourceCat = dockCatOf(pkg)
        val targetCat = flat.getOrNull(target - 1)?.let { dockCatOf(it) }
            ?: flat.getOrNull(target + 1)?.let { dockCatOf(it) }
            ?: sourceCat
        val movedApp = apps.firstOrNull { it.pkg == pkg }

        scope.launch {
            store.update { cfg ->
                val newSections = if (movedApp != null && sourceCat != targetCat) {
                    val eff = AppRepository.sectionsOf(movedApp, cfg)
                    cfg.sections + (pkg to (eff - sourceCat + targetCat))
                } else cfg.sections
                val catOf = { p: String -> if (p == pkg) targetCat else dockCatOf(p) }
                val newOrder = cfg.order.toMutableMap()
                for (cid in orderCats) {
                    val seq = flat.filter { catOf(it) == cid }
                    if (seq.isEmpty()) continue
                    // keep any stored (e.g. hidden) packages not on screen
                    val extras = cfg.order[cid].orEmpty().filter { it !in seq }
                    newOrder[cid] = seq + extras
                }
                cfg.copy(sections = newSections, order = newOrder)
            }
        }
    }

    fun moveHorizontal(delta: Int) =
        if (config.layout == LAYOUT_DOCK) moveDock(delta) else moveWithinRow(delta)

    fun moveVertical(delta: Int) = when (config.layout) {
        LAYOUT_GRID -> moveGridVertical(delta, gridCols)
        LAYOUT_DOCK -> moveDock(if (delta < 0) -dockCols else dockCols)
        else -> moveAcrossRows(delta)
    }

    val moveFocus = remember { FocusRequester() }
    LaunchedEffect(categorized, movePkg) {
        if (movePkg != null) runCatching { moveFocus.requestFocus() }
    }

    // ----- wallpaper: decoded off the main thread, downsampled, cached.
    // A ~100px copy is decoded alongside: upscaled by the GPU it looks
    // blurred, and crossfading it in costs one texture blend per frame
    // instead of a full-screen RenderEffect blur (heavy on TV GPUs). -----
    val wallpaperPair by produceState<Pair<ImageBitmap?, ImageBitmap?>>(
        initialValue = null to null, config.useCustomWallpaper, wallpaperVersion
    ) {
        value = if (!config.useCustomWallpaper) null to null
        else withContext(Dispatchers.IO) {
            val f = File(context.filesDir, "wallpaper.jpg")
            if (!f.exists()) null to null
            else runCatching {
                // 1280px is indistinguishable as a scrimmed background but the
                // texture is ~2.3× smaller than 1920 — TV GPUs choke on the
                // first upload of full-res textures. prepareToDraw() moves the
                // upload off the first visible frame.
                val sharp = decodeDownsampled(f, maxWidth = 1280)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                val blurred = decodeDownsampled(f, maxWidth = 96)
                    ?.also { it.prepareToDraw() }?.asImageBitmap()
                sharp to blurred
            }.getOrDefault(null to null)
        }
    }
    val (wallpaperSharp, wallpaperBlurred) = wallpaperPair
    val presetBrush = remember(config.wallpaper) {
        WALLPAPERS[config.wallpaper.coerceIn(0, WALLPAPERS.size - 1)].brush()
    }

    // ----- Built-in aerial videos: the manifests ship with the launcher, so
    // there's no external dependency — pick one, rotate every 10 minutes. -----
    val aerialWallpaper by produceState<String?>(
        initialValue = null,
        config.useBuiltinAerials, config.builtinSource, wallpaperVersion,
    ) {
        if (!config.useBuiltinAerials) {
            value = null
            return@produceState
        }
        // NOTE: value is intentionally NOT reset here — when the collection
        // changes, the previous video keeps playing until the new one is
        // picked, instead of flashing back to the preset background.
        val aerials = withContext(Dispatchers.IO) {
            com.conreo.couchytv.data.BuiltinAerials.load(context, config.builtinSource)
        }
        if (aerials.isEmpty()) return@produceState
        // Pick one, different from the current so the uri always changes (which
        // drives the fade out→in). The next is chosen when this clip ENDS
        // (onEnded bumps wallpaperVersion, re-running this) — a continuous
        // shuffle. A 10-min safety timer also advances very long clips.
        val current = value
        var next = aerials.random()
        if (aerials.size > 1) while (next == current) next = aerials.random()
        value = next
        delay(10 * 60_000L)
        wallpaperVersion++
    }

    // Aerials stream from a CDN. Right after the TV powers on, the network/DNS
    // isn't up yet, so the first clip fails to load — show the gradient instead
    // of a black screen, and retry: quickly once the network reconnects,
    // otherwise backing off, until a clip streams.
    var aerialError by remember { mutableStateOf(false) }
    LaunchedEffect(aerialError, net.connected) {
        if (!aerialError) return@LaunchedEffect
        delay(if (net.connected) 2500L else 6000L)
        aerialError = false
        wallpaperVersion++
    }

    val corner: Dp = CORNER_RADII[config.cornerRadius.coerceIn(0, CORNER_RADII.size - 1)]
    Box(Modifier.fillMaxSize()) {
    ScaledUi(uiScale) {
    androidx.compose.runtime.CompositionLocalProvider(LocalCornerRadius provides corner) {
    LiteTvTheme(accent = accent) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // MENU always opens launcher settings — the only entry point
                    // when the user hides the status bar.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Menu) {
                        showSettings = true
                        return@onPreviewKeyEvent true
                    }
                    if (movePkg == null) return@onPreviewKeyEvent false
                    // Move mode owns EVERY key event. Ending it must happen on
                    // the KEY-UP: ending on key-down let the release fall
                    // through to the card underneath as a click → app launch.
                    if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                            Key.Back, Key.Escape,
                            -> movePkg = null // drop (or cancel) on release
                            else -> {}
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (event.key) {
                        Key.DirectionLeft -> { moveHorizontal(-1); true }
                        Key.DirectionRight -> { moveHorizontal(1); true }
                        Key.DirectionUp -> { moveVertical(-1); true }
                        Key.DirectionDown -> { moveVertical(1); true }
                        else -> true // swallow select/cancel key-downs too
                    }
                }
        ) {
            // Wallpaper + readability scrim.
            // Priority: built-in aerials > local video > photo > preset.
            val videoSpeed = VIDEO_SPEEDS[config.videoSpeed.coerceIn(0, VIDEO_SPEEDS.size - 1)]
            val aerial = aerialWallpaper
            if (aerial != null && net.connected && !aerialError) {
                VideoWallpaper(
                    uri = aerial,
                    speed = videoSpeed,
                    loop = false, // play once, then shuffle to the next aerial
                    coverBrush = presetBrush,
                    // Stream failed — a dead clip, or (at power-on) the network
                    // isn't ready yet. Fall back to the gradient and retry via
                    // aerialError, instead of sitting on the gradient cover.
                    onEnded = { wallpaperVersion++ },
                    onError = { aerialError = true },
                )
            } else if (config.useVideoWallpaper && config.videoUri.isNotEmpty()) {
                VideoWallpaper(uri = config.videoUri, speed = videoSpeed, loop = true, coverBrush = presetBrush)
            } else if (wallpaperSharp != null) {
                Image(
                    bitmap = wallpaperSharp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(presetBrush))
            }
            // Dock-expanded overlay: cheap pseudo-blur (tiny bitmap upscaled)
            // + dim, crossfaded. Works identically on every Android version.
            val overlayAlpha by animateFloatAsState(
                targetValue = if (dockExpanded) 1f else 0f,
                animationSpec = tween(1000),
                label = "dockOverlay",
            )
            if (overlayAlpha > 0.01f) {
                if (wallpaperBlurred != null) {
                    Image(
                        bitmap = wallpaperBlurred,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha },
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha }
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }
            when (config.scrimMode) {
                4 -> {} // off
                3 -> Box(Modifier.fillMaxSize().background(ScrimFull))
                2 -> Box(Modifier.fillMaxSize().background(ScrimBottom))
                1 -> Box(Modifier.fillMaxSize().background(ScrimTop))
                else -> Box(Modifier.fillMaxSize().background(ScrimTopBottom))
            }

            // Launch entrance: after the boot logo, wait a beat, then the glass
            // panels slide in (status bar from the top, dock from the bottom) and
            // the icons fade in after.
            var introShown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(450); introShown = true }
            val introTop by animateFloatAsState(
                targetValue = if (introShown) 1f else 0f,
                animationSpec = tween(340, easing = FastOutSlowInEasing),
                label = "introTop",
            )

            Column(Modifier.fillMaxSize()) {
                // The status bar sits ABOVE the content, so Up from the top row
                // reaches it — and its height IS the content's top inset (the top
                // padding is a function of the bar's height, nothing hardcoded).
                // When hidden it collapses to an invisible 4dp strip that is still
                // focusable: Up from the top row slides it back into view.
                var statusBarFocused by remember { mutableStateOf(false) }
                val statusBarHeight by animateDpAsState(
                    targetValue = if (config.showStatusBar || statusBarFocused) 80.dp else 4.dp,
                    label = "statusBarHeight",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .clipToBounds()
                        .onFocusChanged { statusBarFocused = it.hasFocus }
                        .focusGroup()
                        // Entrance: slide down from the top + fade in.
                        .graphicsLayer {
                            translationY = (introTop - 1f) * size.height
                            alpha = introTop
                        }
                ) {
                    StatusBar(
                        net = net,
                        time = time,
                        date = date,
                        showVpn = config.showVpnButton,
                        glass = config.statusBarGlass,
                        onVpnClick = {
                            if (config.vpnApp.isNotEmpty()) Actions.launchApp(context, config.vpnApp)
                            else Actions.openVpnSettings(context)
                        },
                        onNetworkClick = { Actions.openNetworkSettings(context) },
                        onSettingsClick = { showSettings = true },
                    )
                }
                when (config.layout) {
                    LAYOUT_DOCK -> DockArea(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        apps = dockApps,
                        config = config,
                        accent = accent,
                        movePkg = movePkg,
                        moveFocus = moveFocus,
                        cardWidth = cardWidth,
                        gap = gap,
                        intro = introShown,
                        expanded = dockExpanded,
                        onExpandChange = { dockExpanded = it },
                        onLaunch = { app -> if (movePkg == null) Actions.launchApp(context, app.pkg) },
                        onMenu = { app -> if (movePkg == null) menuFor = app },
                    )
                    else -> {
                        // Magnetic vertical scroll: the focused row is pinned to a
                        // fixed pivot just below the status bar, so the row above
                        // slides off leaving ~15% of its card peeking (like the
                        // dock's row-1 peek) and the target row drops into place and
                        // snaps. Same peek at the bottom. The pivot leaves room for
                        // the section label + a 15%-card sliver above the focused row.
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        val cardHeightPx = with(density) { (cardWidth * 9f / 16f).toPx() }
                        val peekPx = cardHeightPx * 0.15f
                        val labelPx = with(density) {
                            if (config.showCategoryNames) 34.dp.toPx() else 0f
                        }
                        val pivotPx = peekPx + labelPx
                        val vPivot = remember(pivotPx) {
                            object : androidx.compose.foundation.gestures.BringIntoViewSpec {
                                override fun calculateScrollDistance(
                                    offset: Float,
                                    size: Float,
                                    containerSize: Float,
                                ): Float = offset - pivotPx
                            }
                        }
                        // Rows crossing the top edge fade out (and back in on the way
                        // down) instead of being hard-clipped by an invisible wall.
                        // Kept to the peek sliver so the focused section's label,
                        // which sits just below it, stays fully readable.
                        val fadePx = peekPx
                        // Hoisted out of drawWithContent: rebuilt inside the draw
                        // lambda it re-allocated a Brush (+ shader) every frame.
                        val fadeBrush = remember(fadePx) {
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadePx,
                            )
                        }
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides vPivot
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Restore the last-focused card when focus returns
                                    // (e.g. coming back down from the status bar).
                                    .focusRestorer()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                                    },
                                contentPadding = PaddingValues(
                                    top = with(density) { pivotPx.toDp() },
                                    bottom = with(density) { peekPx.toDp() } + 16.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                        items(categorized.size, key = { categorized[it].first.id }) { rowIndex ->
                            val (cat, catApps) = categorized[rowIndex]
                            val isGrid = config.layout == LAYOUT_GRID
                            // One-shot launch entrance (tied to introShown, so it never
                            // replays on scroll/navigation): carousel rows slide in from
                            // alternating sides and decelerate; the grid blooms per-icon
                            // (inside GridSection). The label fades in after either.
                            val rowEnter by animateFloatAsState(
                                targetValue = if (introShown) 1f else 0f,
                                animationSpec = tween(500, easing = LinearOutSlowInEasing),
                                label = "rowEnter",
                            )
                            val labelAlpha by animateFloatAsState(
                                targetValue = if (introShown) 1f else 0f,
                                animationSpec = tween(280, delayMillis = 320),
                                label = "labelFade",
                            )
                            val slideDir = if (rowIndex % 2 == 0) 1f else -1f
                            Column(
                                modifier = if (!isGrid) Modifier.graphicsLayer {
                                    translationX = (1f - rowEnter) * slideDir * size.width
                                    alpha = rowEnter
                                } else Modifier
                            ) {
                                if (config.showCategoryNames) {
                                    Text(
                                        // Quiet uppercase divider, not competing with
                                        // the icons below it.
                                        text = cat.name.uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.6f),
                                        letterSpacing = 1.5.sp,
                                        // Both grid and carousel start their content at
                                        // the 44dp margin, so the label sits above the
                                        // first icon.
                                        modifier = Modifier
                                            .graphicsLayer { alpha = labelAlpha }
                                            .padding(start = 44.dp, bottom = 10.dp),
                                    )
                                }
                                if (isGrid) {
                                    GridSection(
                                        catApps, config, accent, movePkg, moveFocus,
                                        cardWidth, gap, introShown,
                                        onLaunch = { app -> if (movePkg == null) Actions.launchApp(context, app.pkg) },
                                        onMenu = { app -> if (movePkg == null) menuFor = app },
                                    )
                                } else {
                                    CarouselSection(
                                        catApps, config, accent, movePkg, moveFocus,
                                        cardWidth, gap,
                                        onLaunch = { app -> if (movePkg == null) Actions.launchApp(context, app.pkg) },
                                        onMenu = { app -> if (movePkg == null) menuFor = app },
                                    )
                                }
                            }
                        }
                            }
                        }
                    }
                }
            }

            // ----- dialogs -----
            menuFor?.let { app ->
                AppContextMenu(
                    app = app,
                    isHidden = app.pkg in config.hidden,
                    onDismiss = { menuFor = null },
                    onOpen = { menuFor = null; Actions.launchApp(context, app.pkg) },
                    onMove = { menuFor = null; movePkg = app.pkg },
                    onToggleHide = {
                        menuFor = null
                        scope.launch {
                            store.update {
                                it.copy(
                                    hidden = if (app.pkg in it.hidden) it.hidden - app.pkg
                                    else it.hidden + app.pkg
                                )
                            }
                        }
                    },
                    onAppInfo = { menuFor = null; Actions.openAppInfo(context, app.pkg) },
                    onClose = { menuFor = null; Actions.close(context, app.pkg) },
                    onUninstall = { menuFor = null; Actions.uninstall(context, app.pkg) },
                )
            }

            if (showSettings) {
                SettingsSheet(
                    config = config,
                    apps = apps,
                    store = store,
                    onDismiss = { showSettings = false },
                    onWallpaperChanged = { wallpaperVersion++ },
                    // Runs on LauncherApp's scope, which survives the dialog
                    // teardown — otherwise the setupDone write gets cancelled.
                    onRerunWizard = {
                        showSettings = false
                        scope.launch { store.update { it.copy(setupDone = false) } }
                    },
                )
            }
        }
    }
    }
    }
    // Fondu: the couch boot logo fades out over the freshly-drawn launcher.
    // Wait for a real drawn frame (not just first composition) so the fade
    // begins once the heavy first frame is actually on screen — the breathing
    // logo covers that hitch instead of fading out through it.
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { withFrameNanos {}; withFrameNanos {}; booted = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = !booted,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = androidx.compose.animation.fadeOut(tween(500)),
        modifier = Modifier.fillMaxSize(),
    ) { LoadingScreen() }
    }
}

/* --------------------- carousel: fixed selection --------------------- */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CarouselSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val state = rememberLazyListState()
    // Pivot scrolling done RIGHT: instead of letting the default
    // bring-into-view scroll minimally and then re-pinning by hand (two
    // fighting animations = the janky feel), we replace the scroll spec so
    // the ONLY scroll that ever happens places the focused card at the
    // fixed pivot (the 44dp start padding) in one smooth motion.
    val padPx = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val pivotSpec = remember(padPx) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float,
            ): Float = offset - padPx
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides pivotSpec
    ) {
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 44.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            items(catApps.size, key = { catApps[it].pkg }) { i ->
                val app = catApps[i]
                val moving = app.pkg == movePkg
                AppCard(
                    app = app,
                    isMoving = moving,
                    isHidden = app.pkg in config.hidden,
                    accent = accent,
                    cardWidth = cardWidth,
                    showLabel = config.showAppLabels,
                    modifier = if (moving) Modifier.focusRequester(moveFocus) else Modifier,
                    onLaunch = { onLaunch(app) },
                    onLongPress = { onMenu(app) },
                )
            }
        }
    }
}

/* ------------------- grid: everything on screen ------------------- */

/** Columns are derived from the available width, card size and spacing —
 *  a row can never overflow, so the last icon is never squeezed. */
@Composable
private fun GridSection(
    catApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    intro: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        // fitRow only decides how MANY columns fit; the gap is then expanded so the
        // row fills the width exactly — left margin == right margin (both 44dp) for
        // any icon size / spacing, and every section lines up column-for-column.
        val (cols, _) = fitRow(maxWidth, cardWidth, gap)
        val gapUsed = if (cols > 1) (maxWidth - cardWidth * cols) / (cols - 1) else 0.dp
        val blockWidth = cardWidth * cols + gapUsed * (cols - 1)
        val rows = catApps.chunked(cols)
        // Launch entrance: icons bloom out from the section's centre — the middle
        // tiles pop first (scale + fade), outer ones stagger by distance. One-shot
        // via `intro`; sections composed later (scroll) start settled, no replay.
        val centerR = (rows.size - 1) / 2f
        val centerC = (cols - 1) / 2f
        val maxDist = kotlin.math.hypot(centerC.toDouble(), centerR.toDouble())
            .toFloat().coerceAtLeast(1f)
        Column(
            Modifier.width(blockWidth),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            rows.forEachIndexed { r, rowApps ->
                Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                    rowApps.forEachIndexed { c, app ->
                        val moving = app.pkg == movePkg
                        val dist = kotlin.math.hypot((c - centerC).toDouble(), (r - centerR).toDouble()).toFloat()
                        val bloom by animateFloatAsState(
                            targetValue = if (intro) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 300,
                                delayMillis = (dist / maxDist * 240f).toInt(),
                                easing = FastOutSlowInEasing,
                            ),
                            label = "bloom",
                        )
                        AppCard(
                            app = app,
                            isMoving = moving,
                            isHidden = app.pkg in config.hidden,
                            accent = accent,
                            cardWidth = cardWidth,
                            showLabel = config.showAppLabels,
                            modifier = (if (moving) Modifier.focusRequester(moveFocus) else Modifier)
                                .graphicsLayer {
                                    alpha = bloom
                                    val s = 0.8f + 0.2f * bloom
                                    scaleX = s
                                    scaleY = s
                                },
                            onLaunch = { onLaunch(app) },
                            onLongPress = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

/* ----- dock: floating glass panel with one row; Down = full grid over blurred bg ----- */

@Composable
private fun DockArea(
    modifier: Modifier,
    apps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    cardWidth: Dp,
    gap: Dp,
    intro: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    val dockFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    // Back closes the open grid (returns to the dock); when closed it falls
    // through to the activity's no-op, so Back never exits the home screen.
    androidx.activity.compose.BackHandler(enabled = expanded) { onExpandChange(false) }
    // Which dock column the user pressed Down from, so the grid opens focused on
    // that column of the 2nd row — pressing Down moves down, not back to icon #1.
    var expandFromCol by remember { mutableIntStateOf(0) }
    LaunchedEffect(expanded, intro) {
        if (!intro) return@LaunchedEffect // wait for the launch entrance
        // Claim focus over the first few frames (not after a delay): otherwise the
        // focus, briefly orphaned when the old row's node leaves, snaps to the first
        // focusable — the status-bar VPN icon — and flashes selected before we grab it.
        val target = if (expanded) gridFocus else dockFocus
        repeat(4) {
            withFrameNanos {}
            runCatching { target.requestFocus() }
        }
    }

    // Size + spacing define how many icons the dock holds; the SAME count
    // becomes the grid's columns when unfolded, and both states are centered
    // with the same column math — so icons never shift horizontally.
    BoxWithConstraints(modifier) {
        val panelInnerPadding = 18.dp
        val available = maxWidth - 80.dp - panelInnerPadding * 2
        val (cols, gapUsed) = fitRow(available, cardWidth, gap)
        val cardHeight = cardWidth * 9f / 16f
        val rows = apps.chunked(cols)
        // The grid opens focused on row 0, same column as the dock icon we came
        // from (row 0 is those same icons) — focus stays put, it doesn't jump down.
        val targetRow = 0
        val targetCol = expandFromCol.coerceIn(0, (rows.getOrNull(targetRow)?.size ?: 1) - 1)
        // Fixed block width shared by every row: complete rows are centered,
        // and a partial LAST row stays left-aligned with the columns above.
        val blockWidth = cardWidth * cols + gapUsed * (cols - 1)
        val peekH = cardHeight * 0.22f
        // Distance between the grid's row-0 spot (top, 24dp pad) and the dock's
        // row-0 spot (bottom): the grid slides straight out of / back into the
        // dock icons, so the same icons read as one set rising and sinking.
        val dockRisePx = with(androidx.compose.ui.platform.LocalDensity.current) {
            (maxHeight - peekH - cardHeight - 52.dp).toPx()
        }.toInt().coerceAtLeast(0)
        // Row spacing morphs from a wide "dock" gap down to the tight grid gap as
        // it opens (rows start far apart, like the dock, then draw together), and
        // back out on close.
        val rowGap by animateDpAsState(
            targetValue = if (expanded) gap else gap + cardHeight * 0.35f,
            animationSpec = tween(340, easing = FastOutSlowInEasing),
            label = "rowGap",
        )

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            // Glass panel + row-0 icons, DISSOCIATED so they animate apart: the
            // frosted panel slides DOWN and fades out; the icons fade and drift
            // UP (toward the grid), while the grid rises them into position.
            Box(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                DockGlassPanel(
                    visible = intro && !expanded,
                    rowApps = rows.firstOrNull().orEmpty(),
                    config = config,
                    accent = accent,
                    movePkg = movePkg,
                    moveFocus = moveFocus,
                    dockFocus = dockFocus,
                    cardWidth = cardWidth,
                    gapUsed = gapUsed,
                    innerPadding = panelInnerPadding,
                    onExpandFrom = { i -> expandFromCol = i; onExpandChange(true) },
                    onLaunch = onLaunch,
                    onMenu = onMenu,
                )
            }
            // Teaser: the tops of the 2nd row's cards peeking at the bottom edge.
            // Fades in in SYNC with the dock icons (same delay), so the peek never
            // appears before row 0 on collapse.
            AnimatedVisibility(
                visible = intro && !expanded && rows.size > 1,
                enter = fadeIn(tween(200, delayMillis = 220)),
                exit = fadeOut(tween(200)),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                        rows.getOrNull(1).orEmpty().forEach { app ->
                            PeekStrip(app, cardWidth, cardHeight, peekH)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            // The grid slides straight out of the dock (row 0 aligned with the
            // dock icons) and sinks back into it — one continuous set of icons,
            // ease-in-out (S-curve). Fades in fast so it covers the dock row
            // cleanly instead of double-exposing.
            enter = fadeIn(tween(150)) +
                slideInVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            // Closing: sink back into the dock (S-curve) and only fade out at the
            // very end, so the grid stays solid the whole way down.
            exit = fadeOut(tween(120, delayMillis = 240)) +
                slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { dockRisePx },
            modifier = Modifier.fillMaxSize(),
        ) {
            // Panel is gone; ALL icons over the blurred wallpaper, same column
            // count and same centering as the dock. Up from row 0 returns.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                items(rows.size, key = { rows.getOrNull(it)?.firstOrNull()?.pkg ?: "row$it" }) { r ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.width(blockWidth)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(gapUsed)) {
                                rows[r].forEachIndexed { c, app ->
                                    val moving = app.pkg == movePkg
                                    AppCard(
                                        app = app,
                                        isMoving = moving,
                                        isHidden = app.pkg in config.hidden,
                                        accent = accent,
                                        cardWidth = cardWidth,
                                        showLabel = config.showAppLabels,
                                        // No Up handler: Up from row 0 falls through to
                                        // the status bar. The grid closes with Back.
                                        modifier = when {
                                            moving -> Modifier.focusRequester(moveFocus)
                                            r == targetRow && c == targetCol -> Modifier.focusRequester(gridFocus)
                                            else -> Modifier
                                        },
                                        onLaunch = { onLaunch(app) },
                                        onLongPress = { onMenu(app) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The dock's frosted-glass panel with its row-0 icons — DISSOCIATED so they
 * animate independently on expand: the frosted background slides DOWN and fades
 * out fast, while the icons fade and drift UP as the grid rises them into place.
 * Kept in its own (non-Column) composable so AnimatedVisibility resolves to the
 * plain overload, and so LocalCornerRadius updates the corners live.
 */
@Composable
private fun DockGlassPanel(
    visible: Boolean,
    rowApps: List<AppEntry>,
    config: LauncherConfig,
    accent: Color,
    movePkg: String?,
    moveFocus: FocusRequester,
    dockFocus: FocusRequester,
    cardWidth: Dp,
    gapUsed: Dp,
    innerPadding: Dp,
    onExpandFrom: (Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        // Frosted background — its exit is the exact mirror of its enter: rises
        // up + fades in on close, sinks down + fades out on open (same 300ms fade
        // / 500ms S-curve slide, just reversed).
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)) +
                slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(300)) +
                slideOutVertically(tween(340, easing = FastOutSlowInEasing)) { it },
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(SmoothCornerShape(LocalCornerRadius.current + 14.dp))
                    .background(Color(0xB3121418))
                    .background(Color.White.copy(alpha = 0.06f))
            )
        }
        // Icons — on OPEN they vanish almost instantly (the rising grid takes
        // over); on CLOSE they wait for the grid to sink back before fading in.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200, delayMillis = 220)),
            exit = fadeOut(tween(90)),
        ) {
            Row(
                Modifier.padding(innerPadding),
                horizontalArrangement = Arrangement.spacedBy(gapUsed),
            ) {
                rowApps.forEachIndexed { i, app ->
                    val moving = app.pkg == movePkg
                    AppCard(
                        app = app,
                        isMoving = moving,
                        isHidden = app.pkg in config.hidden,
                        accent = accent,
                        cardWidth = cardWidth,
                        showLabel = config.showAppLabels,
                        modifier = (when {
                            moving -> Modifier.focusRequester(moveFocus)
                            i == 0 -> Modifier.focusRequester(dockFocus)
                            else -> Modifier
                        }).onPreviewKeyEvent { e ->
                            if (movePkg == null &&
                                e.type == KeyEventType.KeyDown &&
                                e.key == Key.DirectionDown
                            ) { onExpandFrom(i); true } else false
                        },
                        onLaunch = { onLaunch(app) },
                        onLongPress = { onMenu(app) },
                    )
                }
            }
        }
    }
}

/**
 * One second-row peek strip: the top slice of a card. It's its OWN composable
 * (not inline) so reading LocalCornerRadius here gives it an independent
 * recomposition scope — roundness changes update it live, exactly like AppCard.
 */
@Composable
private fun PeekStrip(app: AppEntry, cardWidth: Dp, cardHeight: Dp, peekH: Dp) {
    val corner = LocalCornerRadius.current
    Box(
        Modifier
            .width(cardWidth)
            .height(peekH)
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(app.tile),
    ) {
        if (app.banner != null) {
            Image(
                bitmap = app.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                // full card height, parent clips to peekH → only the top slice shows.
                modifier = Modifier.width(cardWidth).height(cardHeight),
            )
        }
    }
}

/**
 * Shared OkHttp client (built once, lazily) that accepts any TLS cert — used
 * ONLY to stream decorative aerial video. Apple's sylvan.apple.com serves a
 * chain many Android-TV trust stores reject, so normal validation kills the
 * wallpaper. No credentials or private data ever go over this client, so
 * skipping validation is acceptable. One instance for the whole app lifetime —
 * OkHttpDataSource never closes it, so sharing is safe.
 */
private val trustAllHttpClient: okhttp3.OkHttpClient by lazy {
    val trustAll = object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()
    }
    val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
    ctx.init(null, arrayOf<javax.net.ssl.TrustManager>(trustAll), java.security.SecureRandom())
    okhttp3.OkHttpClient.Builder()
        .sslSocketFactory(ctx.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

/**
 * Live video wallpaper behind the launcher, muted, aspect-filled, via ExoPlayer.
 * [loop] true replays the same clip (a user's own single video); false plays it
 * once then calls [onEnded] so aerials shuffle to the next. Releases the decoder
 * while the launcher is hidden and rebuilds it on return.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoWallpaper(
    uri: String,
    speed: Float = 1f,
    loop: Boolean = true,
    coverBrush: Brush,
    onEnded: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Slow fondu: a clip is hidden until its FIRST FRAME is actually rendered
    // (i.e. buffered enough to show), then fades up. On a source change the old
    // clip fades out first, so rotations are a symmetric fade out → in instead
    // of a hard cut or a half-buffered pop.
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    var firstFrameGen by remember { mutableIntStateOf(0) }

    // The ExoPlayer — and its hardware video decoder, surface and buffers — is
    // built when the launcher is visible (ON_START) and RELEASED the moment it's
    // hidden (ON_STOP). Holding a scarce hardware codec for a wallpaper nobody's
    // watching while another app is foreground is the launcher's biggest
    // background cost and a needless kill risk; the grid stays warm from cache and
    // the clip re-prepares in a few hundred ms on return. A released decoder also
    // can't churn into a false stall while the SurfaceView has no surface.
    var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    DisposableEffect(lifecycleOwner) {
        fun build(): androidx.media3.exoplayer.ExoPlayer {
            val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true) // fall back to SW decoder on odd clips
            // Stream through a trust-all OkHttp client: Apple's sylvan.apple.com
            // serves a cert chain many TV trust stores can't validate ("Trust
            // anchor not found"), which fails the TLS handshake → the aerial never
            // loads (black). Scoped to the wallpaper player only; it fetches
            // nothing but decorative public video, so there's no data to protect.
            val http = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(trustAllHttpClient)
            val dataSource = androidx.media3.datasource.DefaultDataSource.Factory(context, http)
            val sourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSource)
            return androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setRenderersFactory(renderers)
                .setMediaSourceFactory(sourceFactory)
                // A muted, non-interactive wallpaper never seeks, so the default
                // 50s buffer just pins ~25-30MB of RAM. Trim it — but keep >=15s
                // max so a slow TV network doesn't rebuffer visibly on the loop.
                .setLoadControl(
                    androidx.media3.exoplayer.DefaultLoadControl.Builder()
                        .setBufferDurationsMs(10_000, 15_000, 2_500, 5_000)
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build()
                )
                .build().apply {
                    repeatMode = if (loop) androidx.media3.common.Player.REPEAT_MODE_ONE
                        else androidx.media3.common.Player.REPEAT_MODE_OFF
                    volume = 0f
                    videoScalingMode =
                        androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    playWhenReady = true
                    setPlaybackSpeed(speed)
                }
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START ->
                    if (player == null) player = build()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    player?.let { runCatching { it.release() } }
                    player = null
                }
                else -> {}
            }
        }
        // Already visible when we enter composition (first launch, config change) →
        // build now instead of waiting for the next ON_START.
        if (player == null &&
            lifecycleOwner.lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        ) {
            player = build()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.let { runCatching { it.release() } }
            player = null
        }
    }

    // On release the cover is snapped back opaque so the NEXT visible state shows
    // the gradient (not a black hole) until the rebuilt player renders its frame.
    LaunchedEffect(player == null) { if (player == null) alpha.snapTo(0f) }
    LaunchedEffect(player, speed) { player?.setPlaybackSpeed(speed) }

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() { firstFrameGen++ }
            override fun onPlaybackStateChanged(state: Int) {
                // Clip finished (loop off) → advance to the next aerial.
                if (state == androidx.media3.common.Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("LiteTV", "wallpaper play error ${error.errorCodeName}: $uri")
                onError()
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }
    // Load (or reload, after a rebuild on return) the current clip. Keyed on the
    // player too, so coming back from the background re-prepares on the fresh one.
    LaunchedEffect(player, uri) {
        val p = player ?: return@LaunchedEffect
        // Fade the current clip out before swapping (skipped on the first load, or
        // right after a rebuild, when the cover is already up).
        if (alpha.value > 0f) alpha.animateTo(0f, tween(450))
        val genBefore = firstFrameGen
        runCatching {
            p.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            p.prepare()
        }
        // Stall watchdog: if no frame has rendered in 2.5s (a dead/unreachable
        // stream), skip to the next clip instead of sitting on the cover. Cancelled
        // automatically when the uri (or player) changes.
        delay(2_500L)
        // Only a real stall counts. While the app isn't RESUMED the SurfaceView may
        // have no surface, so NO clip can render — tripping here would falsely flag
        // an error and drop to the gradient. Skip unless resumed.
        val resumed = lifecycleOwner.lifecycle.currentState
            .isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (firstFrameGen == genBefore && resumed) {
            android.util.Log.w("LiteTV", "wallpaper stalled (no frame in 2.5s), skipping: $uri")
            onError()
        }
    }
    // The new clip's first frame is on screen → fade it in slowly.
    LaunchedEffect(firstFrameGen) {
        if (firstFrameGen > 0) alpha.animateTo(1f, tween(1100))
    }
    // SurfaceView, NOT TextureView: it's composited by the system (SurfaceFlinger)
    // on its own thread, so the video never stutters when the UI thread is busy
    // (grid scroll, focus animations) — a TextureView composites on the UI thread
    // and freezes under load. It shows through the transparent window (see
    // MainActivity.setBackgroundDrawable(null)). A SurfaceView can't alpha-blend,
    // so the clip fade is a black cover on top instead: opaque when nothing is
    // shown yet, clear once the frame is up.
    Box(Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> android.view.SurfaceView(ctx) },
            // Bind the surface to whichever player is current. Re-runs when the
            // player is rebuilt on return (the old, released one needs no cleanup);
            // a no-op when it's null after release.
            update = { view -> player?.setVideoSurfaceView(view) },
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = 1f - alpha.value }
                .background(coverBrush)
        )
    }
}

/** Sober startup screen: dark backdrop + a minimal spinning arc, nothing else. */
@Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF101216)),
        contentAlignment = Alignment.Center,
    ) {
        // The couch logo, gently breathing, instead of a generic spinner.
        val transition = rememberInfiniteTransition(label = "load")
        val scale by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                tween(950, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        Image(
            painter = androidx.compose.ui.res.painterResource(com.conreo.couchytv.R.drawable.ic_couch),
            contentDescription = null,
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

/** Decodes a bitmap with an inSampleSize so its width stays near [maxWidth]. */
private fun decodeDownsampled(file: File, maxWidth: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
    }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}
