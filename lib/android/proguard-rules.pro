# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep classes that use reflection for Android Settings access
-keep class org.florisboard.lib.android.AndroidSettings** { *; }
-keep class org.florisboard.lib.android.AndroidSettingsHelper { *; }

# Keep Android Settings classes accessed via reflection
-keep class android.provider.Settings { *; }
-keep class android.provider.Settings$Global { *; }
-keep class android.provider.Settings$Secure { *; }
-keep class android.provider.Settings$System { *; }

# Keep static String fields that are accessed via reflection
-keepclassmembers class android.provider.Settings$Global {
    public static java.lang.String *;
}
-keepclassmembers class android.provider.Settings$Secure {
    public static java.lang.String *;
}
-keepclassmembers class android.provider.Settings$System {
    public static java.lang.String *;
}

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile