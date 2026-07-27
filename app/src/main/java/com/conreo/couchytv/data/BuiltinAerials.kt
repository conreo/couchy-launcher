package com.conreo.couchytv.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Built-in aerial-videos wallpaper source — the video manifests ship WITH the
 * launcher, so no companion app or plugin needs to be installed or configured.
 * 289 videos, 1080p H.264, streamed: Apple (139), Amazon Fire TV (112),
 * community collections (38).
 */
object BuiltinAerials {

    /** Source filter values (config.builtinSource). */
    val SOURCES = listOf("", "A", "Z", "C1", "C2") // "" = all

    @Serializable
    private data class Entry(val t: String = "", val u: String, val s: String = "A")

    // source-key -> video URI
    @Volatile
    private var cache: List<Pair<String, String>>? = null

    private fun loadAll(context: Context): List<Pair<String, String>> {
        cache?.let { return it }
        val list = runCatching {
            val json = context.assets.open("aerials.json")
                .bufferedReader().use { it.readText() }
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<Entry>>(json)
                .map { it.s to it.u }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    /** Video URIs for a collection. [sourceIndex] indexes SOURCES; 0 = every collection. */
    fun load(context: Context, sourceIndex: Int = 0): List<String> {
        val key = SOURCES.getOrElse(sourceIndex) { "" }
        val all = loadAll(context)
        return if (key.isEmpty()) all.map { it.second }
        else all.filter { it.first == key }.map { it.second }
    }
}
