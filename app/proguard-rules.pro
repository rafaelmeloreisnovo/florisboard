# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# Optimization flags for better performance and smaller APK
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''

# Keep Android runtime classes to prevent crashes
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# Keep crash reporting information
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep native methods to prevent JNI crashes
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep Android components to prevent lifecycle crashes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# Keep Compose runtime classes to prevent UI crashes
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# Keep AndroidX and Material3 classes
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep Kotlin Coroutines to prevent async crashes
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Remove logging for release builds (better performance and smaller size)
# Keeping info-level logs for important application events
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Optimize Kotlin metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep Room database classes to prevent data crashes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Prevent crashes from missing enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep IME service classes to prevent keyboard crashes
-keep class dev.patrickgold.florisboard.FlorisImeService { *; }
-keep class dev.patrickgold.florisboard.FlorisSpellCheckerService { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep custom exception handlers for crash reporting
-keep class dev.patrickgold.florisboard.lib.crashutility.** { *; }

# Keep reflection-based classes to prevent runtime crashes
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep WeakReference and related classes for memory management
-keep class java.lang.ref.WeakReference { *; }
-keep class java.lang.ref.SoftReference { *; }

# Prevent crashes from missing R8 optimizations
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep InputMethodService classes
-keep class * extends android.inputmethodservice.InputMethodService { *; }

# Keep lifecycle owners for proper cleanup
-keep class * implements androidx.lifecycle.LifecycleOwner {
    *;
}
