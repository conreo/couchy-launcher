package com.conreo.couchytv.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LocalProfilesTest {
    private val home = LauncherConfig(categories = listOf(CategoryCfg("favourites", "Favourites")), setupDone = true,
        sections = mapOf("video.app" to setOf("favourites")), order = mapOf("favourites" to listOf("video.app")),
        layout = LAYOUT_CAROUSEL, wallpaper = 4, spacing = 2, uiScale = 3)

    @Test fun legacyHomeIsAdoptedWithoutWritingOrResettingAnything() {
        val prefs = mutablePreferencesOf()
        ConfigChanges.update(prefs, home, home.copy(iconScale = 2), false)
        val before = prefs.toPreferences()
        assertEquals("default", LocalProfiles.directory(prefs, "Home")!!.activeId)
        assertEquals("Home", LocalProfiles.directory(prefs, "Home")!!.entries.single().name)
        assertEquals(before, prefs)
        assertEquals(home.copy(iconScale = 2), ConfigChanges.current(prefs, home))
    }

    @Test fun creationCopiesPreferencesButDoesNotSwitchHome() {
        val prefs = mutablePreferencesOf()
        val id = LocalProfiles.create(prefs, home, "Home", " Guest ", false)
        val d = LocalProfiles.directory(prefs, "Home")!!
        assertEquals("default", d.activeId)
        assertEquals("Guest", d.entries.last().name)
        assertEquals(home, d.entries.last().config)
        assertEquals(home, ConfigChanges.current(prefs, home))
        assertNull(d.entries.find { it.id == id }!!.undo)
    }

    @Test fun easyViewOnlyChangesTheNewCopy() {
        val prefs = mutablePreferencesOf()
        LocalProfiles.create(prefs, home, "Home", "Easy", true)
        val copied = LocalProfiles.directory(prefs, "Home")!!.entries.last().config!!
        assertEquals(LAYOUT_GRID, copied.layout)
        assertEquals(3, copied.iconScale)
        assertEquals(3, copied.spacing)
        assertEquals(home.order, copied.order)
        assertEquals(home.wallpaper, copied.wallpaper)
        assertEquals(home.uiScale, copied.uiScale)
        assertEquals(home, ConfigChanges.current(prefs, home))
    }

    @Test fun switchingKeepsSeparateUndoAndSettingsThroughSerialization() {
        val prefs = mutablePreferencesOf()
        val changedHome = home.copy(spacing = 4)
        ConfigChanges.update(prefs, home, changedHome, true)
        val guest = LocalProfiles.create(prefs, changedHome, "Home", "Guest", false)
        LocalProfiles.switch(prefs, changedHome, "Home", guest)
        assertFalse(ConfigChanges.canUndo(prefs))
        val changedGuest = changedHome.copy(wallpaper = 1, iconScale = 3)
        ConfigChanges.update(prefs, changedHome, changedGuest, true)
        LocalProfiles.switch(prefs, changedGuest, "Home", "default")
        assertEquals(changedHome, ConfigChanges.current(prefs, home))
        assertTrue(ConfigChanges.undo(prefs, changedHome))
        assertEquals(home, ConfigChanges.current(prefs, home))
        LocalProfiles.switch(prefs, home, "Home", guest)
        assertEquals(changedGuest, ConfigChanges.current(prefs, home))
        assertTrue(ConfigChanges.undo(prefs, changedGuest))
        assertEquals(changedHome, ConfigChanges.current(prefs, home))
    }

    @Test fun inactiveAppScanStateIsKeptSoNewInstallsCanBeDetected() {
        val prefs = mutablePreferencesOf()
        val guest = LocalProfiles.create(prefs, home, "Home", "Guest", false)
        LocalProfiles.switch(prefs, home.copy(knownApps = setOf("new.app")), "Home", guest)
        assertEquals(home.knownApps, ConfigChanges.current(prefs, home).knownApps)
        assertTrue(ConfigChanges.current(prefs, home).setupDone)
        LocalProfiles.switch(prefs, home, "Home", "default")
        assertEquals(setOf("new.app"), ConfigChanges.current(prefs, home).knownApps)
    }

    @Test fun renameAndInactiveDeletionKeepCurrentPreferences() {
        val prefs = mutablePreferencesOf()
        val guest = LocalProfiles.create(prefs, home, "Home", "Guest", false)
        LocalProfiles.rename(prefs, "Home", "default", "Family")
        assertEquals("Family", LocalProfiles.directory(prefs, "Home")!!.entries.first().name)
        LocalProfiles.delete(prefs, "Home", guest)
        assertEquals(1, LocalProfiles.directory(prefs, "Home")!!.entries.size)
        assertEquals(home, ConfigChanges.current(prefs, home))
        assertThrows(IllegalArgumentException::class.java) { LocalProfiles.delete(prefs, "Home", "default") }
    }

    @Test fun activeProfileCannotBeDeletedAndUnknownSwitchChangesNothing() {
        val prefs = mutablePreferencesOf()
        val id = LocalProfiles.create(prefs, home, "Home", "Guest", false)
        LocalProfiles.switch(prefs, home, "Home", id)
        val before = prefs.toPreferences()
        assertThrows(IllegalArgumentException::class.java) { LocalProfiles.delete(prefs, "Home", id) }
        assertThrows(IllegalArgumentException::class.java) { LocalProfiles.switch(prefs, home, "Home", "missing") }
        assertEquals(before, prefs)
    }

    @Test fun boundedProfilesAndNamesRejectWithoutMutating() {
        val prefs = mutablePreferencesOf()
        listOf("", " ", "x\ny", "x".repeat(33), "home").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { LocalProfiles.create(prefs, home, "Home", name, false) }
        }
        repeat(7) { LocalProfiles.create(prefs, home, "Home", "Person $it", false) }
        val before = prefs.toPreferences()
        assertThrows(IllegalArgumentException::class.java) { LocalProfiles.create(prefs, home, "Home", "Extra", false) }
        assertEquals(before, prefs)
    }

    @Test fun unreadableOrFutureDirectoryIsKeptAndCannotBeOverwritten() {
        listOf("{}", "not json", Json.encodeToString(ProfileDirectory(version = 2, entries = listOf(LocalProfile("default", "Home"))))).forEach { raw ->
            val prefs = mutablePreferencesOf(LocalProfiles.KEY to raw)
            assertNull(LocalProfiles.directory(prefs, "Home"))
            assertThrows(IllegalArgumentException::class.java) { LocalProfiles.create(prefs, home, "Home", "Guest", false) }
            assertEquals(raw, prefs[LocalProfiles.KEY])
            assertEquals(home, ConfigChanges.current(prefs, home))
        }
    }

    @Test fun photosRemainImmutableAcrossCopiesAndUndo() {
        val prefs = mutablePreferencesOf()
        val first = "11111111-1111-1111-1111-111111111111"
        val second = "22222222-2222-2222-2222-222222222222"
        val photoHome = home.copy(useCustomWallpaper = true, photoId = first)
        val guest = LocalProfiles.create(prefs, photoHome, "Home", "Guest", false)
        LocalProfiles.switch(prefs, photoHome, "Home", guest)
        ConfigChanges.update(prefs, photoHome, photoHome.copy(photoId = second), true)
        LocalProfiles.switch(prefs, photoHome.copy(photoId = second), "Home", "default")
        assertEquals(first, ConfigChanges.current(prefs, home).photoId)
        LocalProfiles.switch(prefs, photoHome, "Home", guest)
        assertEquals(second, ConfigChanges.current(prefs, home).photoId)
        assertTrue(ConfigChanges.undo(prefs, photoHome.copy(photoId = second)))
        assertEquals(first, ConfigChanges.current(prefs, home).photoId)
        assertEquals(first, ConfigBackup.forRestore(home, photoHome, true).photoId)
        assertThrows(IllegalArgumentException::class.java) { ConfigBackup.validate(home.copy(photoId = "../../evil")) }
    }
}
