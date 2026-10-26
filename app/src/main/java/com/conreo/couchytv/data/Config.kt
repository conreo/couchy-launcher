package com.conreo.couchytv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.conreo.couchytv.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable

@Serializable
data class CategoryCfg(
    val id: String,
    val name: String,
)

@Immutable
@Serializable
data class LauncherConfig(
    val categories: List<CategoryCfg> = listOf(
        CategoryCfg("streaming", "Streaming"),
        CategoryCfg("games", "Games"),
        CategoryCfg("music", "Music"),
        CategoryCfg("apps", "Apps"),
    ),
    /** package -> section ids the user assigned (an app may be in several) */
    val sections: Map<String, Set<String>> = emptyMap(),
    /** category id -> explicit package order */
    val order: Map<String, List<String>> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    /** packages seen on the last scan — new installs are auto-added to the first section */
    val knownApps: Set<String> = emptySet(),
    val wallpaper: Int = 1, // Aurora
    val useCustomWallpaper: Boolean = false,
    /** Empty keeps the legacy wallpaper.jpg; new photos use immutable, shared-safe file IDs. */
    val photoId: String = "",
    /** video wallpaper: persisted content-URI of a local video, looped muted */
    val videoUri: String = "",
    val useVideoWallpaper: Boolean = false,
    /** built-in aerials source */
    val useBuiltinAerials: Boolean = false,
    /** which built-in collection (index into BuiltinAerials.SOURCES; 0 = all) */
    val builtinSource: Int = 0,
    /** video wallpaper playback speed: index into VIDEO_SPEEDS */
    val videoSpeed: Int = 3, // 1x
    val accent: Int = 0,
    val h24: Boolean = true,
    val showHidden: Boolean = false,
    val setupDone: Boolean = false,
    // ----- status bar -----
    val showStatusBar: Boolean = true,
    /** package of the app the VPN icon opens; empty = system VPN settings */
    val vpnApp: String = "",
    val showVpnButton: Boolean = true,
    /** wrap the status-bar icons in the same glass panel as dock mode */
    val statusBarGlass: Boolean = true,
    /** index into DATE_FORMATS; 0 = no date shown */
    val dateFormat: Int = 0,
    // ----- display options -----
    /** wallpaper dimming: 0 = top & bottom, 1 = top, 2 = bottom, 3 = full, 4 = off */
    val scrimMode: Int = 1, // Top
    val showCategoryNames: Boolean = true,
    val showAppLabels: Boolean = true,
    /** spacing step 0..4 (see GAP_SIZES) */
    val spacing: Int = 1, // Small
    /** icon size step 0..4 (see ICON_SIZES) */
    val iconScale: Int = 1, // Small
    /** icon/panel corner roundness step 0..4 (see CORNER_RADII) */
    val cornerRadius: Int = 3, // Large
    /** UI scale: 0 = Auto (compact high-DPI TVs), 1..5 = fixed (see UI_SCALES) */
    val uiScale: Int = 0, // Auto
    /** language code (e.g. "fr", "de"); empty = system default */
    val language: String = "",
    /** 0 = carousel (fixed selection), 1 = grid, 2 = dock */
    val layout: Int = 2, // Dock
)

const val LAYOUT_CAROUSEL = 0
const val LAYOUT_GRID = 1
const val LAYOUT_DOCK = 2

/** Status bar date formats (SimpleDateFormat patterns); index 0 = off. */
val DATE_FORMATS = listOf("", "EEE d", "EEE d MMM", "d MMM yyyy", "dd/MM", "MM/dd", "yyyy-MM-dd")

/** Sorted by user base — the display name is localized in the UI via string resource. */
val LANGUAGES = listOf(
    "",     // system default
    "zh", "es", "ja", "de", "fr",   // tier 1
    "pt", "ru", "ko", "ar", "it",   // tier 2
    "tr", "pl", "nl", "hi", "th", "in", "vi", // tier 3
)

private val Context.dataStore by preferencesDataStore(name = "launcher")

class ConfigStore(private val context: Context) {

    private fun homeName() = context.getString(R.string.profile_home)

    val state: Flow<LauncherState> = context.dataStore.data.map { prefs ->
        LauncherState(ConfigChanges.current(prefs, initialConfig()), LocalProfiles.directory(prefs, homeName()))
    }
    val flow: Flow<LauncherConfig> = state.map { it.config }

    val canUndo: Flow<Boolean> = context.dataStore.data.map(ConfigChanges::canUndo)

    private fun initialConfig() = LauncherConfig(categories = defaultCategories(context))

    private fun defaultCategories(ctx: Context) = listOf(
        CategoryCfg("streaming", ctx.getString(R.string.cat_streaming)),
        CategoryCfg("games", ctx.getString(R.string.cat_games)),
        CategoryCfg("music", ctx.getString(R.string.cat_music)),
        CategoryCfg("apps", ctx.getString(R.string.cat_apps)),
    )

    suspend fun update(recordUndo: Boolean = true, expectedProfileId: String? = null, transform: (LauncherConfig) -> LauncherConfig) {
        context.dataStore.edit { prefs ->
            if (expectedProfileId != null) check(LocalProfiles.directory(prefs, homeName())?.activeId == expectedProfileId)
            val current = ConfigChanges.current(prefs, initialConfig())
            ConfigChanges.update(prefs, current, transform(current), recordUndo)
        }
    }

    suspend fun restore(backup: LauncherConfig, keepWallpaper: Boolean, expectedProfileId: String? = null) = update(expectedProfileId = expectedProfileId) {
        ConfigBackup.forRestore(backup, it, keepWallpaper)
    }

    suspend fun undo(): Boolean {
        var restored = false
        context.dataStore.edit { prefs -> restored = ConfigChanges.undo(prefs, ConfigChanges.current(prefs, initialConfig())) }
        return restored
    }

    suspend fun createProfile(name: String, easyView: Boolean) {
        context.dataStore.edit { prefs -> LocalProfiles.create(prefs, ConfigChanges.current(prefs, initialConfig()), homeName(), name, easyView) }
    }

    suspend fun switchProfile(id: String) {
        context.dataStore.edit { prefs -> LocalProfiles.switch(prefs, ConfigChanges.current(prefs, initialConfig()), homeName(), id) }
    }

    suspend fun renameProfile(id: String, name: String) {
        context.dataStore.edit { prefs -> LocalProfiles.rename(prefs, homeName(), id, name) }
    }

    suspend fun deleteProfile(id: String) {
        context.dataStore.edit { prefs -> LocalProfiles.delete(prefs, homeName(), id) }
    }
}
