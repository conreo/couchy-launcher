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
