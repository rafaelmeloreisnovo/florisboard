# Keep reflection-based utility functions
-keep class org.florisboard.lib.kotlin.ClassKt { *; }
-keepclassmembers class * {
    public ** simpleNameOrEnclosing();
}

# Keep Companion objects for proper reflection
-keepclassmembers class * {
    public static ** Companion;
}
