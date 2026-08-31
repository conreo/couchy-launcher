package io.xbot.tdlib

/** Thin TDLib JSON client. Native library is packaged from tdlib-kmp's AAR. */
object TdLib {
    fun createClientId(): Int = NativeBridge.createClientId()
    fun send(clientId: Int, request: String) = NativeBridge.send(clientId, request)
    fun receive(timeout: Double): String? = NativeBridge.receive(timeout)
    fun execute(request: String): String? = NativeBridge.execute(request)
}
