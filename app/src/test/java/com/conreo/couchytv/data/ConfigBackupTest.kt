package com.conreo.couchytv.data

import java.io.ByteArrayInputStream
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConfigBackupTest {
    private val home = LauncherConfig(categories = listOf(CategoryCfg("home", "Home")), setupDone = true)

    @Test fun roundTripAndLegacyReaderCompatibility() {
        val source = home.copy(layout = 1, order = mapOf("home" to listOf("com.example.video")))
        val encoded = ConfigBackup.encode(source)
        assertEquals(source, ConfigBackup.decode(encoded))
        assertEquals(source, Json { ignoreUnknownKeys = true }.decodeFromString<LauncherConfig>(encoded))
    }
    @Test fun acceptsOldFlatBackup() {
        assertEquals(home, ConfigBackup.decode(Json { encodeDefaults = true }.encodeToString(home)))
    }
    @Test fun rejectsUnrelatedAndFutureFiles() {
        rejects("{}")
        rejects("{\"name\":\"not a backup\"}")
        rejects(ConfigBackup.encode(home).replace("\"version\": 1", "\"version\": 2"))
        rejects(ConfigBackup.encode(home).replace("\"version\": 1", "\"version\": \"1\""))
    }
    @Test fun rejectsEmptyDuplicateOrInvalidCategories() {
        invalid(home.copy(categories = emptyList()))
        invalid(home.copy(categories = listOf(CategoryCfg("a", "A"), CategoryCfg("a", "B"))))
        invalid(home.copy(categories = listOf(CategoryCfg("a", "\n"))))
    }
    @Test fun rejectsOutOfRangeOptions() {
        listOf(home.copy(layout = -1), home.copy(uiScale = 6), home.copy(videoSpeed = 4),
            home.copy(iconScale = 5), home.copy(dateFormat = 7), home.copy(scrimMode = 8)).forEach(::invalid)
    }
    @Test fun rejectsUnknownSettingsRatherThanQuietlyDroppingThem() {
        rejects(ConfigBackup.encode(home).replace("\"layout\": 2", "\"futureOption\": 7, \"layout\": 2"))
    }
    @Test fun boundsBytesAndRejectsInvalidUtf8() {
        assertTrue(runCatching { ConfigBackup.read(ByteArrayInputStream(ByteArray(ConfigBackup.MAX_BYTES + 1))) }.isFailure)
        assertTrue(runCatching { ConfigBackup.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }.isFailure)
    }
    @Test fun rejectsExcessiveNestingBeforeParsing() {
        rejects("[".repeat(17) + "0" + "]".repeat(17))
    }
    @Test fun doesNotEnableAnImportedNetworkVideo() {
        invalid(home.copy(useVideoWallpaper = true, videoUri = "https://example.test/stream.mp4"))
    }
    @Test fun restorePreservesLocalWallpaperAndDeviceBookkeepingByDefault() {
        val current = home.copy(knownApps = setOf("new.app"), useCustomWallpaper = true, wallpaper = 4, scrimMode = 3)
        val loaded = home.copy(layout = 1, wallpaper = 0, setupDone = false, knownApps = setOf("old.app"),
            useBuiltinAerials = true)
        val restored = ConfigBackup.forRestore(loaded, current, keepWallpaper = true)
        assertEquals(1, restored.layout)
        assertEquals(current.knownApps, restored.knownApps)
        assertTrue(restored.setupDone)
        assertEquals(4, restored.wallpaper)
        assertTrue(restored.useCustomWallpaper)
        assertFalse(restored.useBuiltinAerials)
        assertEquals(3, restored.scrimMode)
    }
    @Test fun optingInRestoresWallpaperSettings() {
        assertEquals(0, ConfigBackup.forRestore(home.copy(wallpaper = 0), home, false).wallpaper)
    }
    @Test fun undoSurvivesSerializationAndKeepsNewAppScan() {
        val prefs = mutablePreferencesOf()
        val next = home.copy(iconScale = 3)
        ConfigChanges.update(prefs, home, next, true)
        assertTrue(ConfigChanges.canUndo(prefs))
        val scanned = next.copy(knownApps = setOf("new.app"))
        ConfigChanges.update(prefs, next, scanned, false)
        assertTrue(ConfigChanges.undo(prefs, scanned))
        assertEquals(home.copy(knownApps = setOf("new.app")), ConfigChanges.current(prefs, home))
        assertFalse(ConfigChanges.canUndo(prefs))
        assertFalse(ConfigChanges.undo(prefs, home))
    }
    @Test fun noOpAndBookkeepingDoNotOverwriteUndo() {
        val prefs = mutablePreferencesOf()
        val next = home.copy(layout = 0)
        ConfigChanges.update(prefs, home, next, true)
        ConfigChanges.update(prefs, next, next, true)
        val scanned = next.copy(knownApps = setOf("new.app"))
        ConfigChanges.update(prefs, next, scanned, true)
        ConfigChanges.undo(prefs, scanned)
        assertEquals(home.layout, ConfigChanges.current(prefs, home).layout)
    }
    private fun rejects(value: String) = assertTrue(runCatching { ConfigBackup.decode(value) }.isFailure)
    private fun invalid(config: LauncherConfig) = assertTrue(runCatching { ConfigBackup.validate(config) }.isFailure)
}
