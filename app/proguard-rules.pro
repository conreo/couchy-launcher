# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.conreo.couchytv.**$$serializer { *; }
-keepclassmembers class com.conreo.couchytv.** {
    *** Companion;
}
-keepclasseswithmembers class com.conreo.couchytv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ZXing QR encoder used for Telegram device-link
-keep class com.google.zxing.** { *; }

# TDLib JNI (io.xbot.tdlib.NativeBridge native methods)
-keep class io.xbot.tdlib.** { *; }

# Hidden API exemption used by Close (force-stop + clear cache)
-keep class org.lsposed.hiddenapibypass.** { *; }
