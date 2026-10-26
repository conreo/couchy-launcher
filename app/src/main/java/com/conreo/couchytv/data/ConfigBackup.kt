package com.conreo.couchytv.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A flat, backwards-readable settings file. Media files and document grants are device-local. */
object ConfigBackup {
    const val MAX_BYTES = 256 * 1024
    private const val METADATA = "_couchyBackup"
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    fun encode(config: LauncherConfig): String {
        validate(config)
        val fields = json.parseToJsonElement(json.encodeToString(config)).jsonObject
        return json.encodeToString(JsonObject(fields + (METADATA to JsonObject(mapOf("version" to JsonPrimitive(1))))))
            .also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
    }

    fun read(input: InputStream): LauncherConfig {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(4096)
        while (true) {
            val count = input.read(chunk)
            if (count == -1) break
            require(out.size() + count <= MAX_BYTES) { "Backup too large" }
            out.write(chunk, 0, count)
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray())).toString()
        return decode(text)
    }

    fun decode(text: String): LauncherConfig {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup too large" }
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 16) { "Backup too deeply nested" } }
                '}', ']' -> depth--
            }
        }
        val fields = json.parseToJsonElement(text).jsonObject
        // An empty/unrelated JSON object otherwise decodes as defaults and silently resets Home.
        require("categories" in fields) { "Not a settings backup" }
        fields[METADATA]?.let {
            val version = it.jsonObject["version"]?.jsonPrimitive
            require(version != null && !version.isString && version.intOrNull == 1) { "Unsupported backup version" }
        }
        return json.decodeFromString<LauncherConfig>(JsonObject(fields - METADATA).toString()).also(::validate)
    }

    fun validate(config: LauncherConfig) {
        require(config.categories.size in 1..32)
        require(config.categories.map { it.id }.toSet().size == config.categories.size)
        config.categories.forEach { require(validText(it.id, 128) && validText(it.name, 512)) }
        require(config.wallpaper in 0..4 && config.accent in 0..5 && config.layout in 0..2)
        require(config.scrimMode in 0..4 && config.spacing in 0..4 && config.iconScale in 0..4)
        require(config.cornerRadius in 0..4 && config.uiScale in 0..5 && config.dateFormat in 0..6)
        require(config.videoSpeed in 0..3 && config.builtinSource in 0..128)
        require(config.language.length <= 35 && config.language.all { it.isLetterOrDigit() || it == '-' })
        require(config.videoUri.length <= 4096 && config.vpnApp.length <= 255)
        require(config.photoId.isEmpty() || config.photoId.matches(Regex("[a-f0-9-]{36}")))
        require(config.hidden.size <= 2048 && config.knownApps.size <= 2048 && config.sections.size <= 2048 && config.order.size <= 32)
        (config.hidden + config.knownApps + config.sections.keys + config.order.values.flatten()).forEach { require(validText(it, 255)) }
        config.sections.values.forEach { ids -> require(ids.size <= 32); ids.forEach { require(validText(it, 128)) } }
        config.order.forEach { (id, apps) -> require(validText(id, 128) && apps.size <= 2048 && apps.distinct().size == apps.size) }
        if (config.useVideoWallpaper) require(config.videoUri.startsWith("content://") || config.videoUri.startsWith("android.resource://"))
    }

    private fun validText(value: String, max: Int) = value.isNotBlank() && value.length <= max && value.none { it.isISOControl() }

    fun forRestore(backup: LauncherConfig, current: LauncherConfig, keepWallpaper: Boolean): LauncherConfig {
        validate(backup)
        val restored = backup.copy(knownApps = current.knownApps, setupDone = current.setupDone)
        return if (!keepWallpaper) restored else restored.copy(
            wallpaper = current.wallpaper, useCustomWallpaper = current.useCustomWallpaper, photoId = current.photoId,
            videoUri = current.videoUri, useVideoWallpaper = current.useVideoWallpaper,
            useBuiltinAerials = current.useBuiltinAerials, builtinSource = current.builtinSource,
            videoSpeed = current.videoSpeed, scrimMode = current.scrimMode,
        )
    }
}
