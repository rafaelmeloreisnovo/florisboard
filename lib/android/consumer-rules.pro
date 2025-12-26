# Keep reflection-based Android Settings classes
# AndroidSettings.kt uses reflection to access Settings.Global, Settings.Secure, and Settings.System
-keep class android.provider.Settings { *; }
-keep class android.provider.Settings$Global { *; }
-keep class android.provider.Settings$Secure { *; }
-keep class android.provider.Settings$System { *; }
-keepclassmembers class android.provider.Settings$Global {
    public static java.lang.String *;
}
-keepclassmembers class android.provider.Settings$Secure {
    public static java.lang.String *;
}
-keepclassmembers class android.provider.Settings$System {
    public static java.lang.String *;
}

# Keep AndroidSettingsHelper and its subclasses
-keep class org.florisboard.lib.android.AndroidSettingsHelper { *; }
-keep class org.florisboard.lib.android.AndroidSettings** { *; }
