package io.xbot.tdlib

/**
 * JNI names must match `libtdjsonjava.so` from tdlib-kmp (package + method
 * names are part of the ABI). Do not rename this object or the native methods.
 */
internal object NativeBridge {
    init {
        System.loadLibrary("tdjsonjava")
    }

    fun createClientId(): Int = nativeCreateClientId()
    fun send(clientId: Int, request: String) = nativeSend(clientId, request)
    fun receive(timeout: Double): String? = nativeReceive(timeout)
    fun execute(request: String): String? = nativeExecute(request)

    @JvmStatic private external fun nativeCreateClientId(): Int
    @JvmStatic private external fun nativeSend(clientId: Int, request: String)
    @JvmStatic private external fun nativeReceive(timeout: Double): String?
    @JvmStatic private external fun nativeExecute(request: String): String?
}
