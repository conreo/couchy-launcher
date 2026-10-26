package com.conreo.couchytv.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Both keys are changed inside one DataStore edit; a crash cannot separate a restore from its undo. */
internal object ConfigChanges {
    val CONFIG = stringPreferencesKey("config")
    private val UNDO = stringPreferencesKey("config_undo")
    private val json = Json { ignoreUnknownKeys = true }

    fun current(prefs: Preferences, fallback: LauncherConfig): LauncherConfig = decode(prefs[CONFIG]) ?: fallback
    fun canUndo(prefs: Preferences) = decode(prefs[UNDO]) != null
    fun undoConfig(prefs: Preferences): LauncherConfig? = decode(prefs[UNDO])

    fun replaceForProfile(prefs: MutablePreferences, config: LauncherConfig, undo: LauncherConfig?) {
        prefs[CONFIG] = json.encodeToString(config)
        if (undo == null) prefs.remove(UNDO) else prefs[UNDO] = json.encodeToString(undo)
    }

    fun update(prefs: MutablePreferences, current: LauncherConfig, next: LauncherConfig, recordUndo: Boolean) {
        if (current == next) return
        // Package scans and wizard bookkeeping must not displace a person's last settings action.
        if (recordUndo && current.copy(knownApps = next.knownApps, setupDone = next.setupDone) != next)
            prefs[UNDO] = json.encodeToString(current)
        prefs[CONFIG] = json.encodeToString(next)
    }

    fun undo(prefs: MutablePreferences, current: LauncherConfig): Boolean {
        val previous = decode(prefs[UNDO]) ?: return false
        prefs[CONFIG] = json.encodeToString(previous.copy(knownApps = current.knownApps, setupDone = current.setupDone))
        prefs.remove(UNDO)
        return true
    }

    private fun decode(value: String?): LauncherConfig? = value?.let {
        runCatching { json.decodeFromString<LauncherConfig>(it) }.getOrNull()
    }
}
