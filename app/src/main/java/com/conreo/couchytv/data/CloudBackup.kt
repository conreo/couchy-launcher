package com.conreo.couchytv.data

import android.content.Context
import android.os.Build
import com.conreo.couchytv.BuildConfig
import io.xbot.tdlib.TdLib
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Opt-in Telegram backup: the TV logs in as the user (QR on the TV, scan with
 * the phone Telegram app; optional two-step password) and sends/reads a
 * `.couchybak` file in Saved Messages. Nothing is uploaded until the user
 * picks Save in the Cloud.
 *
 * Requires `telegram.api.id` + `telegram.api.hash` from https://my.telegram.org
 * at build time (local.properties / env). Empty = Cloud options toast
 * "not configured".
 */
class TelegramCloudBackup private constructor(private val app: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startMutex = Mutex()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val started = AtomicBoolean(false)

    private var clientId: Int = 0
    private var receiveJob: Job? = null
    private var lastAuthType: String = ""
    private var lastQrLink: String = ""
    private var lastPasswordHint: String = ""
    private var userId: Long = 0L
    private var loginRequested: Boolean = false

    private val _session = MutableStateFlow(CloudSession.Empty)
    val session: StateFlow<CloudSession> = _session.asStateFlow()

    private val _authUi = MutableStateFlow<TelegramAuthUi>(TelegramAuthUi.Idle)
    val authUi: StateFlow<TelegramAuthUi> = _authUi.asStateFlow()

    fun isConfigured(): Boolean =
        BuildConfig.TELEGRAM_API_ID != 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()

    /** Opens the TDLib client so [session] can show an existing Saved-Messages login. */
    fun warmup() {
        if (isConfigured()) scope.launch { runCatching { ensureStarted() } }
    }

    fun beginLogin() {
        loginRequested = true
        _authUi.value = TelegramAuthUi.Working
        when (lastAuthType) {
            "authorizationStateReady" -> _authUi.value = TelegramAuthUi.Success
            "authorizationStateWaitPassword" ->
                _authUi.value = TelegramAuthUi.Password(lastPasswordHint)
            "authorizationStateWaitOtherDeviceConfirmation" ->
                if (lastQrLink.isNotBlank()) _authUi.value = TelegramAuthUi.Qr(lastQrLink)
            "authorizationStateWaitPhoneNumber" -> requestQr()
        }
        scope.launch { ensureStarted() }
    }

    suspend fun awaitLoggedIn() {
        val ui = _authUi.first { it is TelegramAuthUi.Success || it is TelegramAuthUi.Failed }
        if (ui is TelegramAuthUi.Failed) error(ui.message)
    }

    suspend fun submitPassword(password: String) = withContext(Dispatchers.IO) {
        val result = runCatching {
            sendAwait(
                buildJsonObject {
                    put("@type", JsonPrimitive("checkAuthenticationPassword"))
                    put("password", JsonPrimitive(password))
                },
            )
        }
        val err = result.exceptionOrNull()?.message.orEmpty()
        if (result.isFailure || result.getOrNull()?.str("@type") == "error") {
            _authUi.value = TelegramAuthUi.Password(
                hint = lastPasswordHint,
                error = err.ifBlank { "PASSWORD_HASH_INVALID" },
            )
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching {
            sendAwait(buildJsonObject { put("@type", JsonPrimitive("logOut")) })
        }
        loginRequested = false
        userId = 0L
        _session.value = CloudSession.Empty
        _authUi.value = TelegramAuthUi.Idle
    }

    suspend fun saveConfig(jsonText: String) = withContext(Dispatchers.IO) {
        ensureReady()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val timeLabel = SimpleDateFormat("HH:mm yyyy-MM-dd", Locale.US).format(Date())
        val file = File(app.cacheDir, "CouchyBackup_${stamp}.$EXT")
        file.writeText(jsonText)
        val chatId = savedMessagesChatId()
        val caption = "$TAG\nname=$timeLabel Couchy backup"
        sendAwait(
            buildJsonObject {
                put("@type", JsonPrimitive("sendMessage"))
                put("chat_id", JsonPrimitive(chatId))
                put(
                    "input_message_content",
                    buildJsonObject {
                        put("@type", JsonPrimitive("inputMessageDocument"))
                        put(
                            "document",
                            buildJsonObject {
                                put("@type", JsonPrimitive("inputFileLocal"))
                                put("path", JsonPrimitive(file.absolutePath))
                            },
                        )
                        put(
                            "caption",
                            buildJsonObject {
                                put("@type", JsonPrimitive("formattedText"))
                                put("text", JsonPrimitive(caption))
                            },
                        )
                    },
                )
            },
            timeoutMs = 90_000,
        )
    }

    /** Raw JSON of the newest Couchy backup in Saved Messages, or null. */
    suspend fun loadConfig(): String? = withContext(Dispatchers.IO) {
        ensureReady()
        val chatId = savedMessagesChatId()
        val found = sendAwait(
            buildJsonObject {
                put("@type", JsonPrimitive("searchChatMessages"))
                put("chat_id", JsonPrimitive(chatId))
                put("query", JsonPrimitive(TAG))
                put("from_message_id", JsonPrimitive(0))
                put("offset", JsonPrimitive(0))
                put("limit", JsonPrimitive(20))
                put(
                    "filter",
                    buildJsonObject { put("@type", JsonPrimitive("searchMessagesFilterDocument")) },
                )
            },
        )
        val msg = pickLatestBackup(found) ?: return@withContext null
        val fileObj = msg["content"]?.jsonObject
            ?.get("document")?.jsonObject
            ?.get("document")?.jsonObject
            ?: return@withContext null
        val local = fileObj["local"]?.jsonObject
        val existing = local?.str("path").orEmpty()
        if (local?.str("is_downloading_completed") == "true" && existing.isNotBlank()) {
            return@withContext File(existing).readText()
        }
        val fileId = fileObj.int("id")
        if (fileId == 0) return@withContext null
        val downloaded = sendAwait(
            buildJsonObject {
                put("@type", JsonPrimitive("downloadFile"))
                put("file_id", JsonPrimitive(fileId))
                put("priority", JsonPrimitive(1))
                put("offset", JsonPrimitive(0))
                put("limit", JsonPrimitive(0))
                put("synchronous", JsonPrimitive(true))
            },
            timeoutMs = 90_000,
        )
        val path = downloaded["local"]?.jsonObject?.str("path").orEmpty()
        if (path.isBlank()) return@withContext null
        File(path).readText()
    }

    private suspend fun ensureReady() {
        if (!isConfigured()) throw CloudNotConfigured()
        ensureStarted()
        withTimeout(45_000) {
            while (true) {
                when (lastAuthType) {
                    "authorizationStateReady" -> break
                    "authorizationStateWaitPhoneNumber",
                    "authorizationStateWaitOtherDeviceConfirmation",
                    "authorizationStateWaitPassword",
                    -> throw NeedTelegramLogin()
                }
                delay(50)
            }
        }
        if (!_session.value.signedIn) runCatching { refreshMe() }
        if (!_session.value.signedIn) throw NeedTelegramLogin()
    }

    private suspend fun savedMessagesChatId(): Long {
        if (userId == 0L) refreshMe()
        val chat = sendAwait(
            buildJsonObject {
                put("@type", JsonPrimitive("createPrivateChat"))
                put("user_id", JsonPrimitive(userId))
                put("force", JsonPrimitive(true))
            },
        )
        val id = chat.long("id")
        return if (id != 0L) id else userId
    }

    private suspend fun refreshMe() {
        val me = sendAwait(buildJsonObject { put("@type", JsonPrimitive("getMe")) })
        userId = me.long("id")
        val username = me["usernames"]?.jsonObject?.str("editable_username")
            ?.ifBlank { null }
            ?.let { "@$it" }
        val name = me.str("first_name").trim()
        val label = username ?: name.ifBlank { userId.toString() }
        _session.value = CloudSession(signedIn = true, accountLabel = label)
    }

    private suspend fun ensureStarted() {
        if (!isConfigured()) throw CloudNotConfigured()
        startMutex.withLock {
            if (started.get()) return
            TdLib.execute("""{"@type":"setLogVerbosityLevel","new_verbosity_level":1}""")
            clientId = TdLib.createClientId()
            receiveJob = scope.launch { receiveLoop() }
            started.set(true)
        }
    }

    private fun requestQr() {
        scope.launch {
            runCatching {
                sendAwait(
                    buildJsonObject {
                        put("@type", JsonPrimitive("requestQrCodeAuthentication"))
                        put("other_user_ids", buildJsonArray {})
                    },
                )
            }.onFailure { e ->
                _authUi.value = TelegramAuthUi.Failed(e.message ?: "QR login failed")
            }
        }
    }

    private suspend fun receiveLoop() {
        while (true) {
            val raw = runCatching { TdLib.receive(1.0) }.getOrNull()
            if (raw.isNullOrBlank()) continue
            val obj = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            }.getOrNull() ?: continue
            val extra = obj.str("@extra")
            if (extra.isNotBlank()) pending.remove(extra)?.complete(obj)
            if (obj.str("@type") == "updateAuthorizationState") {
                obj["authorization_state"]?.jsonObject?.let { handleAuth(it) }
            }
        }
    }

    private fun handleAuth(state: JsonObject) {
        val type = state.str("@type")
        lastAuthType = type
        when (type) {
            "authorizationStateWaitTdlibParameters" -> scope.launch {
                runCatching { sendAwait(tdlibParameters()) }.onFailure { e ->
                    _authUi.value = TelegramAuthUi.Failed(e.message ?: "TDLib init failed")
                }
            }
            "authorizationStateWaitPhoneNumber" -> {
                _session.value = CloudSession.Empty
                if (loginRequested) requestQr()
            }
            "authorizationStateWaitOtherDeviceConfirmation" -> {
                lastQrLink = state.str("link")
                if (loginRequested && lastQrLink.isNotBlank()) {
                    _authUi.value = TelegramAuthUi.Qr(lastQrLink)
                }
            }
            "authorizationStateWaitPassword" -> {
                lastPasswordHint = state.str("password_hint")
                if (loginRequested) {
                    _authUi.value = TelegramAuthUi.Password(lastPasswordHint)
                }
            }
            "authorizationStateWaitCode",
            "authorizationStateWaitEmailAddress",
            "authorizationStateWaitEmailCode",
            "authorizationStateWaitRegistration",
            "authorizationStateWaitPremiumPurchase",
            -> if (loginRequested) {
                _authUi.value = TelegramAuthUi.Failed(type)
            }
            "authorizationStateReady" -> {
                scope.launch { runCatching { refreshMe() } }
                if (loginRequested) _authUi.value = TelegramAuthUi.Success
            }
            "authorizationStateLoggingOut",
            "authorizationStateClosing",
            -> {
                _session.value = CloudSession.Empty
            }
            "authorizationStateClosed" -> {
                started.set(false)
                _session.value = CloudSession.Empty
            }
        }
    }

    private fun tdlibParameters(): JsonObject {
        val dir = File(app.filesDir, "tdlib").apply { mkdirs() }.absolutePath
        return buildJsonObject {
            put("@type", JsonPrimitive("setTdlibParameters"))
            put("use_test_dc", JsonPrimitive(false))
            put("database_directory", JsonPrimitive(dir))
            put("files_directory", JsonPrimitive(dir))
            put("database_encryption_key", JsonPrimitive(""))
            put("use_file_database", JsonPrimitive(true))
            put("use_chat_info_database", JsonPrimitive(true))
            put("use_message_database", JsonPrimitive(true))
            put("use_secret_chats", JsonPrimitive(false))
            put("api_id", JsonPrimitive(BuildConfig.TELEGRAM_API_ID))
            put("api_hash", JsonPrimitive(BuildConfig.TELEGRAM_API_HASH))
            put("system_language_code", JsonPrimitive(Locale.getDefault().language.ifBlank { "en" }))
            put("device_model", JsonPrimitive("Android TV (${Build.MODEL})"))
            put("system_version", JsonPrimitive(Build.VERSION.RELEASE ?: ""))
            put("application_version", JsonPrimitive(BuildConfig.VERSION_NAME))
        }
    }

    private suspend fun sendAwait(body: JsonObject, timeoutMs: Long = 60_000): JsonObject {
        ensureStarted()
        val extra = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pending[extra] = deferred
        val payload = buildJsonObject {
            body.forEach { (k, v) -> put(k, v) }
            put("@extra", JsonPrimitive(extra))
        }
        TdLib.send(clientId, payload.toString())
        val result = try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (t: Throwable) {
            pending.remove(extra)
            throw t
        }
        if (result.str("@type") == "error") {
            error("${result.int("code")} ${result.str("message")}".trim())
        }
        return result
    }

    private fun pickLatestBackup(found: JsonObject): JsonObject? {
        val messages = found["messages"] as? JsonArray ?: return null
        return messages.map { it.jsonObject }
            .filter { isCouchyBackup(it) }
            .maxByOrNull { it.long("date") }
    }

    private fun isCouchyBackup(msg: JsonObject): Boolean {
        val content = msg["content"]?.jsonObject ?: return false
        if (content.str("@type") != "messageDocument") return false
        val caption = content["caption"]?.jsonObject?.str("text").orEmpty()
        if (caption.startsWith(TAG)) return true
        val name = content["document"]?.jsonObject?.str("file_name").orEmpty()
        return name.endsWith(".$EXT") || name.contains("CouchyBackup")
    }

    companion object {
        const val LOCAL_FILE = ConfigStore.LOCAL_FILE
        const val TAG = "COUCHY_BACKUP"
        const val EXT = "couchybak"

        @Volatile private var instance: TelegramCloudBackup? = null

        fun get(context: Context): TelegramCloudBackup {
            return instance ?: synchronized(this) {
                instance ?: TelegramCloudBackup(context.applicationContext).also { instance = it }
            }
        }
    }
}

data class CloudSession(
    val signedIn: Boolean = false,
    val accountLabel: String = "",
) {
    companion object {
        val Empty = CloudSession()
    }
}

sealed class TelegramAuthUi {
    data object Idle : TelegramAuthUi()
    data object Working : TelegramAuthUi()
    data class Qr(val link: String) : TelegramAuthUi()
    data class Password(val hint: String, val error: String? = null) : TelegramAuthUi()
    data object Success : TelegramAuthUi()
    data class Failed(val message: String) : TelegramAuthUi()
}

class NeedTelegramLogin : Exception()
class CloudNotConfigured : Exception()

private fun JsonObject.str(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.int(key: String): Int =
    this[key]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

private fun JsonObject.long(key: String): Long =
    this[key]?.jsonPrimitive?.longOrNull
        ?: this[key]?.jsonPrimitive?.content?.toLongOrNull()
        ?: 0L
