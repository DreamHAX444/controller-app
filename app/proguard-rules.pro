# Supabase and Ktor Proguard Rules
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepnames class * {
    @kotlinx.serialization.Serializable *;
}

# Ktor
-keep class io.ktor.** { *; }

# Supabase
-keep class io.github.jan.supabase.** { *; }
