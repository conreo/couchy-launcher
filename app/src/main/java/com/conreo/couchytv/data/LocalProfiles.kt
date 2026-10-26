package com.conreo.couchytv.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LocalProfile(
    val id: String,
    val name: String,
    // The active profile uses the original config/undo keys, keeping old installs compatible.
    val config: LauncherConfig? = null,
    val undo: LauncherConfig? = null,
)

@Serializable
data class ProfileDirectory(val version: Int = 1, val activeId: String = "default", val entries: List<LocalProfile>)

data class LauncherState(val config: LauncherConfig, val profiles: ProfileDirectory?) {
    val activeProfileId: String get() = profiles?.activeId ?: "default"
}

/** All switches and their per-profile undo points share one DataStore transaction. */
internal object LocalProfiles {
    const val MAX_PROFILES = 8
    val KEY = stringPreferencesKey("local_profiles")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val idPattern = Regex("default|[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")

    private fun save(prefs: MutablePreferences, directory: ProfileDirectory) {
        val encoded = json.encodeToString(directory)
        require(encoded.length <= 4 * 1024 * 1024)
        prefs[KEY] = encoded
    }

    fun directory(prefs: Preferences, homeName: String): ProfileDirectory? {
        val raw = prefs[KEY] ?: return ProfileDirectory(entries = listOf(LocalProfile("default", homeName)))
        return runCatching {
            require(raw.length <= 4 * 1024 * 1024)
            json.decodeFromString<ProfileDirectory>(raw).also { d ->
                require(d.version == 1 && d.entries.size in 1..MAX_PROFILES)
                require(d.entries.map { it.id }.toSet().size == d.entries.size)
                require(d.entries.any { it.id == "default" } && d.entries.any { it.id == d.activeId })
                require(d.entries.map { it.name.lowercase(java.util.Locale.ROOT) }.toSet().size == d.entries.size)
                d.entries.forEach { p ->
                    require(p.id.matches(idPattern) && validName(p.name))
                    if (p.id != d.activeId) require(p.config != null)
                    p.config?.let(ConfigBackup::validate)
                    p.undo?.let(ConfigBackup::validate)
                }
            }
        }.getOrNull()
    }

    fun validName(name: String) = name == name.trim() && name.isNotEmpty() && name.length <= 32 && name.none { it.isISOControl() }

    fun create(prefs: MutablePreferences, current: LauncherConfig, homeName: String, name: String, easyView: Boolean): String {
        val d = requireNotNull(directory(prefs, homeName))
        require(d.entries.size < MAX_PROFILES)
        val clean = name.trim()
        require(validName(clean) && d.entries.none { it.name.equals(clean, ignoreCase = true) })
        val id = UUID.randomUUID().toString()
        val copy = if (easyView) current.copy(layout = LAYOUT_GRID, iconScale = 3, spacing = 3, showAppLabels = true, showCategoryNames = true) else current
        save(prefs, d.copy(entries = d.entries + LocalProfile(id, clean, copy)))
        return id
    }

    fun switch(prefs: MutablePreferences, current: LauncherConfig, homeName: String, targetId: String) {
        val d = requireNotNull(directory(prefs, homeName))
        if (targetId == d.activeId) return
        val target = requireNotNull(d.entries.find { it.id == targetId })
        val next = requireNotNull(target.config).copy(setupDone = current.setupDone)
        val entries = d.entries.map {
            when (it.id) {
                d.activeId -> it.copy(config = current, undo = ConfigChanges.undoConfig(prefs))
                targetId -> it.copy(config = null, undo = null)
                else -> it
            }
        }
        save(prefs, d.copy(activeId = targetId, entries = entries))
        ConfigChanges.replaceForProfile(prefs, next, target.undo)
    }

    fun rename(prefs: MutablePreferences, homeName: String, id: String, name: String) {
        val d = requireNotNull(directory(prefs, homeName))
        val clean = name.trim()
        require(d.entries.any { it.id == id } && validName(clean))
        require(d.entries.none { it.id != id && it.name.equals(clean, ignoreCase = true) })
        save(prefs, d.copy(entries = d.entries.map { if (it.id == id) it.copy(name = clean) else it }))
    }

    fun delete(prefs: MutablePreferences, homeName: String, id: String) {
        val d = requireNotNull(directory(prefs, homeName))
        require(id != "default" && id != d.activeId && d.entries.any { it.id == id })
        save(prefs, d.copy(entries = d.entries.filterNot { it.id == id }))
        // Photo files may still be referenced by another profile or undo point; don't remove them here.
    }
}
