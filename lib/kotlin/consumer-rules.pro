# Keep reflection-based utility functions
-keep class org.florisboard.lib.kotlin.ClassKt { *; }

# Keep Companion objects for proper reflection
-keepclassmembers class * {
    public static ** Companion;
}
