# KomgaTV ProGuard 규칙
# 릴리스 빌드 시 코드 난독화 및 최적화 설정

# Kotlin Serialization 규칙
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.komgatv.**$$serializer { *; }
-keepclassmembers class com.komgatv.** {
    *** Companion;
}
-keepclasseswithmembers class com.komgatv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit 규칙
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# OkHttp 규칙
-dontwarn okhttp3.**
-dontwarn okio.**

# Room 규칙
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**
