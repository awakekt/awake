# ProGuard keep rules for Awake Core's prebuilt desktop/macOS player (`awake-player.jar`).
# Awake Core libraries also contribute their own consumer rules via META-INF/proguard/*.pro.

-keep class com.awakekt.awake.player.shell.MainKt {
    public static void main(java.lang.String[]);
}

# Keep shrinking & obfuscation on, but disable string-folding optimizations that can exceed
# JVM's 64 KB UTF-8 constant-pool entry limit in `--classfile` output (unlike Android DEX).
-dontoptimize

# Preserve annotations and generic signatures (omit kotlin.Metadata and PermittedSubclasses:
# kotlin.Metadata can exceed JVM's 64 KB UTF-8 constant-pool limit in `--classfile` output, and
# PermittedSubclasses breaks JVM sealed-hierarchy verification when classes are obfuscated).
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep kotlinx.serialization companions and generated serializers.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.awakekt.awake.**$$serializer { *; }
-keepclassmembers class com.awakekt.awake.** {
    *** Companion;
}
-keepclasseswithmembers class com.awakekt.awake.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Optional/platform-specific references absent from desktop JREs.
-dontwarn android.**
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn kotlin.annotations.jvm.**
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.serialization.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.slf4j.**
-dontwarn sun.misc.**
